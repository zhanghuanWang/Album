package com.gallery.core.ui.fragment

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.kotlin.expand.app.*
import androidx.kotlin.expand.content.findUriByFileExpand
import androidx.kotlin.expand.content.openVideoExpand
import androidx.kotlin.expand.net.orEmptyExpand
import androidx.kotlin.expand.os.camera.CameraStatus
import androidx.kotlin.expand.os.getParcelableOrDefault
import androidx.kotlin.expand.os.orEmptyExpand
import androidx.kotlin.expand.os.permission.PermissionCode
import androidx.kotlin.expand.view.hideExpand
import androidx.kotlin.expand.view.showExpand
import androidx.recyclerview.widget.GridLayoutManager
import com.gallery.core.GalleryBundle
import com.gallery.core.R
import com.gallery.core.ResultType
import com.gallery.core.callback.*
import com.gallery.core.ext.*
import com.gallery.core.ui.adapter.GalleryAdapter
import com.gallery.core.ui.base.GalleryBaseFragment
import com.gallery.core.ui.widget.SimpleGridDivider
import com.gallery.scan.*
import com.yalantis.ucrop.UCrop
import kotlinx.android.synthetic.main.gallery_fragment_gallery.*


class ScanFragment : GalleryBaseFragment(R.layout.gallery_fragment_gallery), ScanView, GalleryAdapter.OnGalleryItemClickListener, IGallery {

    companion object {
        fun newInstance(galleryBundle: GalleryBundle = GalleryBundle()): ScanFragment {
            val scanFragment = ScanFragment()
            val bundle = Bundle()
            bundle.putParcelable(IGallery.GALLERY_START_CONFIG, galleryBundle)
            scanFragment.arguments = bundle
            return scanFragment
        }
    }

    private val galleryInterceptor by lazy {
        when {
            parentFragment is IGalleryInterceptor -> parentFragment as IGalleryInterceptor
            activity is IGalleryInterceptor -> activity as IGalleryInterceptor
            else -> object : IGalleryInterceptor {}
        }
    }
    private val galleryImageLoader by lazy {
        when {
            parentFragment is IGalleryImageLoader -> parentFragment as IGalleryImageLoader
            activity is IGalleryImageLoader -> activity as IGalleryImageLoader
            else -> object : IGalleryImageLoader {}
        }
    }
    private val galleryCallback by lazy {
        when {
            parentFragment is IGalleryCallback -> parentFragment as IGalleryCallback
            activity is IGalleryCallback -> activity as IGalleryCallback
            else -> throw IllegalArgumentException(context.toString() + " must implement IGalleryCallback")
        }
    }
    private val galleryBundle by lazy {
        getParcelableOrDefault<GalleryBundle>(IGallery.GALLERY_START_CONFIG, GalleryBundle())
    }
    private val galleryAdapter by lazy {
        GalleryAdapter(requireActivity().squareExpand(galleryBundle.spanCount), galleryBundle, galleryCallback, galleryImageLoader, this)
    }
    private val scan by lazy {
        ScanImpl(this)
    }
    private val startActivityForResult: ActivityResultContracts.StartActivityForResult = ActivityResultContracts.StartActivityForResult()

    private val activityResult: ActivityResultCallback<ActivityResult> = ActivityResultCallback<ActivityResult> { intent ->
        val bundleExpand = intent?.data?.extras.orEmptyExpand()
        when (intent.resultCode) {
            Activity.RESULT_OK -> {
                val cropUri = bundleExpand.getParcelable<Uri>(UCrop.EXTRA_OUTPUT_URI)
                if (cropUri == null) {
                    galleryInterceptor.onUCropError(requireContext(), null)
                    return@ActivityResultCallback
                }
                galleryInterceptor.onUCropResources(cropUri.orEmptyExpand())
                cropUri.path?.let { scanFile(ResultType.CROP, it) }
            }
            Activity.RESULT_CANCELED -> {
                galleryInterceptor.onUCropCanceled(requireContext())
            }
            UCrop.RESULT_ERROR -> {
                galleryInterceptor.onUCropError(requireContext(), UCrop.getError(intent?.data.orEmptyExpand()))
            }
        }
    }

    private var fileUri: Uri = Uri.EMPTY
    var parentId: Long = SCAN_ALL

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(IGallery.GALLERY_START_SELECT, selectEntities)
        outState.putLong(IGallery.GALLERY_START_PARENT_ID, parentId)
        outState.putParcelable(IGallery.GALLERY_START_IMAGE_URL, fileUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            parentId = it.getLong(IGallery.GALLERY_START_PARENT_ID, SCAN_ALL)
            fileUri = it.getParcelableOrDefault(IGallery.GALLERY_START_IMAGE_URL, Uri.EMPTY)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        galleryRootView.setBackgroundColor(galleryBundle.galleryRootBackground)
        galleryEmpty.setImageDrawable(drawableExpand(galleryBundle.photoEmptyDrawable))
        galleryEmpty.setOnClickListener { v ->
            if (galleryInterceptor.onEmptyPhotoClick(v)) {
                startCamera()
            }
        }
        galleryAdapter.addSelectAll(savedInstanceState?.getParcelableArrayList(IGallery.GALLERY_START_SELECT)
                ?: galleryBundle.selectEntities)
        galleryRecyclerView.setHasFixedSize(true)
        galleryRecyclerView.layoutManager = GridLayoutManager(requireActivity(), galleryBundle.spanCount)
        galleryRecyclerView.addItemDecoration(SimpleGridDivider(galleryBundle.dividerWidth))
        galleryRecyclerView.adapter = galleryAdapter
        savedInstanceState?.let { galleryCallback.onChangedScreen(selectEntities.size) }
        onScanGallery(parentId)
    }

    override fun scanSuccess(arrayList: ArrayList<ScanEntity>) {
        if (arrayList.isEmpty() && parentId.isScanAll()) {
            galleryEmpty.showExpand()
            galleryCallback.onScanSuccessEmpty(requireContext())
            return
        }
        galleryCallback.onScanSuccess(arrayList)
        galleryEmpty.hideExpand()
        if (parentId.isScanAll() && !galleryBundle.hideCamera) {
            arrayList.add(0, ScanEntity(parent = IGallery.CAMERA_PARENT_ID))
        }
        galleryAdapter.addAll(arrayList)
        galleryAdapter.updateEntity()
    }

    override fun resultSuccess(scanEntity: ScanEntity?) {
        if (scanEntity == null) {
            galleryCallback.onCameraResultError(requireContext(), galleryBundle)
        } else {
            if (parentId.isScanAll()) {
                galleryAdapter.addEntity(if (galleryBundle.hideCamera) 0 else 1, scanEntity)
            } else if (parentId == scanEntity.parent) {
                galleryAdapter.addEntity(0, scanEntity)
            }
            galleryAdapter.notifyDataSetChanged()
            galleryCallback.onScanResultSuccess(requireContext(), galleryBundle, scanEntity)
        }
    }

    override fun onCameraItemClick(view: View, position: Int, galleryEntity: ScanEntity) {
        startCamera()
    }

    override fun onPhotoItemClick(view: View, position: Int, galleryEntity: ScanEntity) {
        if (!moveToNextToIdExpand(galleryEntity.externalUri())) {
            galleryCallback.onClickItemFileNotExist(requireContext(), galleryBundle, galleryEntity)
            return
        }
        if (galleryBundle.scanType == ScanType.VIDEO) {
            requireContext().openVideoExpand(galleryEntity.externalUri()) {
                galleryCallback.onOpenVideoPlayError(requireContext(), galleryEntity)
            }
            return
        }
        if (galleryBundle.radio) {
            if (galleryBundle.crop) {
                openCrop(galleryEntity.externalUri())
            } else {
                galleryCallback.onGalleryResource(requireContext(), galleryEntity)
            }
            return
        }
        galleryCallback.onPhotoItemClick(requireContext(), galleryBundle, galleryEntity, position, parentId)
    }

    override fun onScanGallery(parent: Long, result: Boolean) {
        if (!checkPermissionAndRequestWrite()) {
            return
        }
        this.parentId = parent
        // 如果本机没有图片,进来拍照直接走扫描全部的方法可以兼容到hideCamera
        // resultSuccess只有在拍照成功并且之前数据不为空or裁剪成功才会回调
        // 拍照成功之后不需要特殊处理,因为肯定是SCAN_ALL的情况下,直接更新数据即可
        // 裁剪成功分为两种情况
        // 第一种:SCAN_ALL情况下直接更新数据
        // 第二种:parentId为文件夹ID的时候处理数据,如果 parentId == scan.id
        // 可以直接插入到当前数据,如果不等于,不能插入,因为裁剪之后的图片属于另一个文件夹的数据
        // 文件夹数据更新的时候也需要处理这种情况
        if (result && galleryAdapter.isNotEmpty) {
            scan.scanResult(findIdByUriExpand(fileUri))
        } else {
            scan.scanParent(parent)
        }
    }

    override fun scanFile(type: ResultType, path: String) {
        MediaScannerConnection.scanFile(requireContext(), arrayOf(path), null) { _: String?, uri: Uri? ->
            runOnUiThreadExpand {
                uri ?: return@runOnUiThreadExpand
                fileUri = uri
                onScanGallery(parentId, type == ResultType.CROP)
            }
        }
    }

    override fun startCamera() {
        if (!checkPermissionAndRequestCamera()) {
            galleryCallback.onCameraOpenStatus(requireContext(), CameraStatus.PERMISSION, galleryBundle)
            return
        }
        fileUri = requireActivity().findUriByFileExpand(requireActivity().galleryPathFile(galleryBundle.cameraPath, galleryBundle.cameraName, galleryBundle.scanType))
        galleryCallback.onCameraOpenStatus(requireContext(), openCamera(CameraUri(galleryBundle.scanType, fileUri)), galleryBundle)
    }

    override fun onCameraResultCanceled() {
        galleryCallback.onCameraCanceled(requireContext(), galleryBundle)
    }

    override fun onCameraResultOk() {
        findPathByUriExpand(fileUri)?.let {
            scanFile(ResultType.CAMERA, it)
            if (galleryBundle.cameraCrop) {
                openCrop(fileUri)
            }
        }
    }

    override fun openCrop(uri: Uri) {
        val onGalleryCustomCrop = galleryInterceptor.onCustomPhotoCrop(uri)
        if (onGalleryCustomCrop) {
            return
        }
        val intent: Intent = UCrop.of(uri, Uri.fromFile(requireActivity().cropPathFile(galleryBundle.uCropPath, galleryBundle.cameraName, galleryBundle.scanType)))
                .withOptions(galleryInterceptor.onUCropOptions())
                .getIntent(requireContext())
        registerForActivityResult(startActivityForResult, activityResult).launch(intent)
    }

    override fun onUpdatePrevResult(bundle: Bundle) {
        val previewGalleryEntity = bundle.getParcelableArrayList<ScanEntity>(IGalleryPrev.PREV_RESULT_SELECT)
        val isRefreshUI = bundle.getBoolean(IGalleryPrev.PREV_RESULT_REFRESH, true)
        if (!isRefreshUI || previewGalleryEntity == null || selectEntities == previewGalleryEntity) {
            return
        }
        galleryAdapter.addSelectAll(previewGalleryEntity)
        galleryAdapter.updateEntity()
        galleryCallback.onChangedPrevCount(selectCount)
    }

    override fun permissionsGranted(type: PermissionCode) {
        when (type) {
            PermissionCode.WRITE -> onScanGallery(parentId, result = false)
            PermissionCode.READ -> startCamera()
        }
    }

    override fun permissionsDenied(type: PermissionCode) {
        galleryCallback.onPermissionsDenied(requireContext(), type)
    }

    override val currentScanType: ScanType
        get() = galleryBundle.scanType

    override val scanContext: FragmentActivity
        get() = requireActivity()

    override val currentEntities: ArrayList<ScanEntity>
        get() = galleryAdapter.currentList.filter { it.parent != IGallery.CAMERA_PARENT_ID } as ArrayList<ScanEntity>

    override val selectEntities: ArrayList<ScanEntity>
        get() = galleryAdapter.currentSelectList

    override val selectEmpty: Boolean
        get() = selectEntities.isEmpty()

    override val selectCount: Int
        get() = selectEntities.size

    override val itemCount: Int
        get() = currentEntities.size
}