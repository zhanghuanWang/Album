package com.gallery.sample.viewmodel

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.gallery.scan.SCAN_ALL
import com.gallery.scan.ScanImpl
import com.gallery.scan.ScanType
import com.gallery.scan.ScanViewModelFactory

object ScanViewModelTest {

    fun test(fragmentActivity: FragmentActivity) {
//        runCatching { ViewModelProvider(fragmentActivity).get(ScanImpl::class.java) }
//                .onSuccess { Log.i("ViewModelProvider", "ViewModelProvider success") }
//                .onFailure { Log.e("ViewModelProvider", "ViewModelProvider failure:${it.message}") }
        val viewModel = ViewModelProvider(fragmentActivity, ScanViewModelFactory(fragmentActivity, ScanType.IMAGE)).get(ScanImpl::class.java)
        viewModel.scanLiveData.observe(fragmentActivity, Observer {
            Log.i("ViewModelProvider", "ViewModelProvider success:${it.entities}")
        })
        viewModel.resultLiveData.observe(fragmentActivity, Observer {
            Log.i("ViewModelProvider", "ViewModelProvider success:${it.entity}")
        })
        viewModel.scanParent(SCAN_ALL)
    }
}