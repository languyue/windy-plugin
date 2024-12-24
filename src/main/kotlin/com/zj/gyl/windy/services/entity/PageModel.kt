package com.zj.gyl.windy.services.entity

class PageModel<T>(var total: Int, var data: ArrayList<T>) {

    fun getList(): ArrayList<T>{
        return data
    }
}