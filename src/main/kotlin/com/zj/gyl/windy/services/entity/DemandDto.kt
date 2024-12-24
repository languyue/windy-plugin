package com.zj.gyl.windy.services.entity

class DemandDto(var demandName: String, var demandId: String, var createTime: Long, var level: Int, var status: Int) {

    override fun toString(): String {
        return demandName
    }
}