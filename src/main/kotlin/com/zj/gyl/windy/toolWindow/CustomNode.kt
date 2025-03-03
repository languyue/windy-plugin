package com.zj.gyl.windy.toolWindow

import javax.swing.tree.DefaultMutableTreeNode

class CustomNode: DefaultMutableTreeNode {

    var name: String
    var relatedId: String
    var loading: Boolean = false

    constructor(userObject: String, relatedId: String, loading: Boolean): super(userObject){
        this.name = userObject
        this.loading = loading
        this.relatedId = relatedId
    }

    constructor(userObject: String, relatedId: String):this(userObject, relatedId, false)
}