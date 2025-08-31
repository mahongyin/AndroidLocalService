package com.android.local.service.demo.service

import com.android.local.service.annotation.Request
import com.android.local.service.annotation.ServicePort
import com.android.local.service.demo.livedata.LiveDataHelper

@ServicePort(port = 3333)
abstract class OtherService {

    @Request("query")
    fun query(
        aaa: Boolean,
        bbb: Double,
        ccc: Float,
        ddd: String,
        eee: Int,
    ): List<String> {
        return listOf("$aaa", "$bbb", "$ccc", "$ddd", "$eee")
    }

    @Request("delete")
    fun delete(id: Int, name: String) {
        LiveDataHelper.saveDataLiveData.postValue("id=${id},name=${name}");
    }
}