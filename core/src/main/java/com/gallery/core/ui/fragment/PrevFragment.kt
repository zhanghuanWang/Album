package com.gallery.core.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.kotlin.expand.app.moveToNextToIdExpand
import androidx.kotlin.expand.os.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.gallery.core.GalleryBundle
import com.gallery.core.GalleryConfig
import com.gallery.core.R
import com.gallery.core.callback.IGalleryPrev
import com.gallery.core.expand.externalUri
import com.gallery.core.ui.adapter.PrevAdapter
import com.gallery.core.ui.base.GalleryBaseFragment
import com.gallery.scan.ScanEntity
import com.gallery.scan.ScanImpl
import com.gallery.scan.ScanViewModelFactory
import com.gallery.scan.annotation.ScanTypeDef
import kotlinx.android.synthetic.main.gallery_fragment_preview.*

open class PrevFragment : GalleryBaseFragment(R.layout.gallery_fragment_preview), IGalleryPrev {

    companion object {
        fun newInstance(parentId: Long,
                        selectList: ArrayList<ScanEntity>,
                        config: GalleryBundle,
                        position: Int,
                        @ScanTypeDef
                        aloneScan: Int): PrevFragment {
            val prevFragment = PrevFragment()
            val bundle = Bundle()
            bundle.putInt(GalleryConfig.GALLERY_RESULT_SCAN_ALONE, aloneScan)
            bundle.putParcelable(GalleryConfig.GALLERY_CONFIG, config)
            bundle.putParcelableArrayList(GalleryConfig.GALLERY_SELECT, selectList)
            bundle.putLong(GalleryConfig.GALLERY_PARENT_ID, parentId)
            bundle.putInt(GalleryConfig.GALLERY_POSITION, position)
            prevFragment.arguments = bundle
            return prevFragment
        }
    }

    private val pageChangeCallback: ViewPager2.OnPageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                galleryPrevCallback.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }

            override fun onPageScrollStateChanged(state: Int) {
                galleryPrevCallback.onPageScrollStateChanged(state)
            }

            override fun onPageSelected(position: Int) {
                galleryPrevCallback.onPageSelected(position)
                if (!galleryPrevInterceptor.hideCheckBox) {
                    preCheckBox.isSelected = isCheckBox(position)
                }
            }
        }
    }
    private val prevAdapter: PrevAdapter by lazy { PrevAdapter { entity, container -> galleryImageLoader.onDisplayGalleryPrev(entity, container) } }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(GalleryConfig.GALLERY_POSITION, currentPosition)
        outState.putParcelableArrayList(GalleryConfig.GALLERY_SELECT, selectEntities)
    }

    private fun updateEntity(arrayList: ArrayList<ScanEntity>, savedInstanceState: Bundle?) {
        prevAdapter.addAll(arrayList)
        prevAdapter.addSelectAll((savedInstanceState ?: bundleOrEmptyExpand()).getParcelableArrayListExpand(GalleryConfig.GALLERY_SELECT))
        prevAdapter.updateEntity()

        galleryPrevCallback.onPrevViewCreated(savedInstanceState)
        preViewPager.adapter = prevAdapter
        preViewPager.registerOnPageChangeCallback(pageChangeCallback)
        preRootView.setBackgroundColor(galleryBundle.prevPhotoBackgroundColor)
        setCurrentItem((savedInstanceState ?: bundleOrEmptyExpand()).getIntExpand(GalleryConfig.GALLERY_POSITION))

        if (!galleryPrevInterceptor.hideCheckBox) {
            preCheckBox.setBackgroundResource(galleryBundle.checkBoxDrawable)
            preCheckBox.setOnClickListener { checkBoxClick(preCheckBox) }
            preCheckBox.isSelected = isCheckBox(currentPosition)
            preCheckBox.visibility = View.VISIBLE
        } else {
            preCheckBox.visibility = View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //https://github.com/7449/Album/issues/4
        //新增对单独扫描的支持
        val scanAlone = getIntOrDefault(GalleryConfig.GALLERY_RESULT_SCAN_ALONE, GalleryConfig.DEFAULT_SCAN_ALONE_TYPE)
        val parentId: Long = getLongExpand(GalleryConfig.GALLERY_PARENT_ID)
        if (parentId == GalleryConfig.DEFAULT_PARENT_ID) {
            //如果是点击预览这里认为未选中数据和选中数据应该一致
            updateEntity(getParcelableArrayListExpand(GalleryConfig.GALLERY_SELECT), savedInstanceState)
        } else {
            //https://issuetracker.google.com/issues/127692541
            //这个问题已经在ViewPager2上修复
            val scanViewModel = ViewModelProvider(requireActivity(), ScanViewModelFactory(requireActivity(),
                    if (scanAlone == GalleryConfig.DEFAULT_SCAN_ALONE_TYPE) galleryBundle.scanType else scanAlone,
                    galleryBundle.scanSort,
                    galleryBundle.scanSortField))
                    .get(ScanImpl::class.java)
            scanViewModel.scanLiveData.observe(requireActivity(), Observer { updateEntity(it.entities, savedInstanceState) })
            scanViewModel.scanParent(parentId)
        }
    }

    fun checkBoxClick(preCheckBox: View) {
        if (!moveToNextToIdExpand(currentItem.externalUri)) {
            if (prevAdapter.containsSelect(currentItem)) {
                prevAdapter.removeSelectEntity(currentItem)
            }
            preCheckBox.isSelected = false
            currentItem.isSelected = false
            galleryPrevCallback.onClickCheckBoxFileNotExist(requireActivity(), galleryBundle, currentItem)
            return
        }
        if (!prevAdapter.containsSelect(currentItem) && selectEntities.size >= galleryBundle.multipleMaxCount) {
            galleryPrevCallback.onClickCheckBoxMaxCount(requireActivity(), galleryBundle, currentItem)
            return
        }
        if (currentItem.isSelected) {
            prevAdapter.removeSelectEntity(currentItem)
            currentItem.isSelected = false
            preCheckBox.isSelected = false
        } else {
            prevAdapter.addSelectEntity(currentItem)
            currentItem.isSelected = true
            preCheckBox.isSelected = true
        }
        galleryPrevCallback.onChangedCheckBox()
    }

    override val currentItem: ScanEntity
        get() = prevAdapter.item(currentPosition)

    override val allItem: ArrayList<ScanEntity>
        get() = prevAdapter.allItem

    override val selectEntities: ArrayList<ScanEntity>
        get() = prevAdapter.currentSelectList

    override val selectEmpty: Boolean
        get() = selectEntities.isEmpty()

    override val selectCount: Int
        get() = selectEntities.size

    override val itemCount: Int
        get() = prevAdapter.itemCount

    override val currentPosition: Int
        get() = preViewPager.currentItem

    override fun isCheckBox(position: Int): Boolean {
        return prevAdapter.isCheck(position)
    }

    override fun setCurrentItem(position: Int) {
        setCurrentItem(position, false)
    }

    override fun setCurrentItem(position: Int, smoothScroll: Boolean) {
        preViewPager.setCurrentItem(position, smoothScroll)
    }

    override fun notifyItemChanged(position: Int) {
        prevAdapter.notifyItemChanged(position)
    }

    override fun notifyDataSetChanged() {
        prevAdapter.notifyDataSetChanged()
    }

    override fun resultBundle(isRefresh: Boolean): Bundle {
        val bundle = Bundle()
        bundle.putParcelableArrayList(GalleryConfig.GALLERY_SELECT, selectEntities)
        bundle.putBoolean(GalleryConfig.GALLERY_RESULT_REFRESH, isRefresh)
        return bundle
    }

    override fun onDestroyView() {
        preViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }
}