package com.example.widgetapp.widget

import android.view.View
import android.view.ViewGroup

/**
 * 所有 Widget 的基类。
 *
 * 对应一块 XML 布局（或已有 View），可以嵌套子 Widget。
 * 本身没有生命周期，适合纯展示、不依赖宿主回调的视图。
 * 需要跟 Activity / Fragment 走的用 [LifecycleWidget]，
 * 放进 RecyclerView 的用 [RecyclerItemWidget]。
 */
abstract class Widget {

    /** 这个 Widget 管的根 View。子类自己 inflate，或包一层已经有的 View。 */
    abstract val root: View

    private val children = mutableListOf<Widget>()

    /** 父 Widget。一个 Widget 只能属于一个父 Widget，避免生命周期与回收回调被重复分发。 */
    private var parentWidget: Widget? = null

    /**
     * 当前直接子 Widget，返回的是副本。
     * 只读，增删请走 [addChild] / [removeChild]，这样才会触发对应回调。
     */
    val childWidgets: List<Widget> get() = children.toList()

    /**
     * 登记一个直接子 Widget。
     *
     * 只维护父子关系，不会自动把子 View 加进布局，布局结构仍由 XML 或调用方决定。
     * 重复添加同一个实例会被忽略；同一个 Widget 不能同时登记到多个父节点。
     * 添加成功后调用 [onChildAdded]。[LifecycleWidget] 会在这个时机把已绑定的宿主传给子 Widget。
     *
     * @param widget 要登记的子 Widget，不能是自己。
     */
    protected fun addChild(widget: Widget) {
        check(widget !== this) { "Widget cannot add itself as a child" }
        if (widget.parentWidget === this) return
        check(widget.parentWidget == null) { "Widget already has a parent. Remove it before adding it to another Widget." }
        children += widget
        widget.parentWidget = this
        onChildAdded(widget)
    }

    /**
     * 解除和某个直接子 Widget 的关系。
     *
     * 不存在时什么都不做。移除成功后调用 [onChildRemoved]。
     * 不会把子 View 从父容器里摘掉，需要的话再调 [detachFromParent]。
     */
    protected fun removeChild(widget: Widget) {
        if (children.remove(widget)) {
            widget.parentWidget = null
            onChildRemoved(widget)
        }
    }

    /**
     * 子 Widget 刚被登记。子类在这里做跟随逻辑，例如把生命周期宿主传下去。
     * 默认空实现。
     */
    protected open fun onChildAdded(child: Widget) {}

    /**
     * 子 Widget 刚被移除。子类在这里做对应的清理，例如让它脱离宿主。
     * 默认空实现。
     */
    protected open fun onChildRemoved(child: Widget) {}

    /**
     * 把 [root] 挂到 [parent] 上。
     *
     * 如果 [root] 已经有父容器，会先从原来的父容器移除，避免重复 addView 崩溃。
     * [params] 为 null 时用子 View 自己已有的 LayoutParams。
     * 这只是视图挂载，不是生命周期绑定；生命周期用 [LifecycleWidget.attach]。
     */
    open fun attachTo(parent: ViewGroup, params: ViewGroup.LayoutParams? = null) {
        (root.parent as? ViewGroup)?.removeView(root)
        if (params == null) parent.addView(root) else parent.addView(root, params)
    }

    /**
     * 把 [root] 从当前父容器移除。没有父容器时什么都不做。
     * 不会解除生命周期或 RecyclerView 的绑定。
     */
    open fun detachFromParent() {
        (root.parent as? ViewGroup)?.removeView(root)
    }
}
