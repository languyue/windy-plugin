package com.zj.gyl.windy.services

interface DataLoadListener {
    fun load()

    fun expire()
}