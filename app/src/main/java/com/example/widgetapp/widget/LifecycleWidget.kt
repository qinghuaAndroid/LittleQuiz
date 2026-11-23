package com.example.widgetapp.widget

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 跟宿主生命周期走的 Widget。
 *
 * [attach] 到 Activity（传 activity 自己）或 Fragment（建议传 viewLifecycleOwner）之后，
 * 回调与宿主一致：onCreate / onStart / onResume / onPause / onStop / onDestroy。
 * 后挂上的 Widget 会立刻补齐当前已经走过的回调。
 * 嵌套的 [LifecycleWidget] 会挂到同一个宿主，各自注册，互不抢回调。
 *
 * 宿主走到 DESTROYED 时会自动解除注册。提前 [detach] 会补一次 [onDestroy]，方便只在那里做清理。
 */
abstract class LifecycleWidget : Widget() {

    private var host: LifecycleOwner? = null

    /** 当前绑定的宿主。还没 [attach]，或已经 [detach] / 宿主销毁后，为 null。 */
    val lifecycleHost: LifecycleOwner? get() = host

    /** 是否还挂在某个宿主上。宿主销毁后会变成 false。 */
    val isAttached: Boolean get() = host != null

    /** 正在分发宿主的 ON_DESTROY。用来避免 [detach] 把还没收到回调的子 Widget 提前摘掉。 */
    private var dispatchingDestroy = false

    /**
     * 真正注册到宿主 Lifecycle 上的观察者。
     *
     * 不让 Widget 自己实现 [DefaultLifecycleObserver]，是为了把「宿主事件」和
     * 「提前 detach 时补一次 onDestroy」分开，子类覆写时也不用记得调 super。
     * addObserver 是同步的：宿主如果已经 STARTED / RESUMED，这里会立刻往下补回调。
     */
    private val observer = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) = this@LifecycleWidget.onCreate(owner)
        override fun onStart(owner: LifecycleOwner) = this@LifecycleWidget.onStart(owner)
        override fun onResume(owner: LifecycleOwner) = this@LifecycleWidget.onResume(owner)
        override fun onPause(owner: LifecycleOwner) = this@LifecycleWidget.onPause(owner)
        override fun onStop(owner: LifecycleOwner) = this@LifecycleWidget.onStop(owner)
        override fun onDestroy(owner: LifecycleOwner) {
            dispatchingDestroy = true
            try {
                this@LifecycleWidget.onDestroy(owner)
            } finally {
                dispatchingDestroy = false
                unbindSelf()
            }
        }
    }

    /**
     * 绑定到 [owner]，之后生命周期回调与宿主一致。
     *
     * Activity 传 `this`。Fragment 建议传 `viewLifecycleOwner`，这样视图销毁就会走 [onDestroy]，
     * 而不是等到 Fragment 实例本身销毁。
     *
     * 已经绑在同一个宿主上时直接返回。换宿主会先 [detach] 旧的，再注册新的。
     * 注册是同步的，宿主已经走过的 onCreate / onStart / onResume 会马上补进来。
     * 如果补回调期间自己又 [detach] 或改绑了别的宿主，就不再把子 Widget 挂到这次传入的 [owner]。
     * 已有的 [LifecycleWidget] 子节点会一起 [attach] 到同一个宿主。
     */
    fun attach(owner: LifecycleOwner) {
        if (host === owner) return
        detach()
        host = owner
        owner.lifecycle.addObserver(observer)
        if (host !== owner) return
        for (child in childWidgets) {
            if (child is LifecycleWidget) child.attach(owner)
        }
    }

    /**
     * 提前离开宿主。
     *
     * 已经不在宿主上时什么都不做。正常调用会先解除注册，再补一次 [onDestroy]，
     * 然后让子 [LifecycleWidget] 也 [detach]。
     *
     * 如果是在宿主自己的 [onDestroy] 回调里又调了本方法：观察者已经会清掉，
     * 不再补一次 [onDestroy]，也不去拆子 Widget，避免子节点收不到宿主销毁回调。
     */
    fun detach() {
        val owner = host ?: return
        if (dispatchingDestroy) {
            unbindSelf()
            return
        }
        unbindSelf()
        onDestroy(owner)
        for (child in childWidgets) {
            if (child is LifecycleWidget) child.detach()
        }
    }

    /**
     * 只解除自己和宿主的注册，不动子 Widget。
     * 重复调用是安全的。
     */
    private fun unbindSelf() {
        val owner = host ?: return
        owner.lifecycle.removeObserver(observer)
        host = null
    }

    /**
     * 父 Widget 已经绑了宿主时，后加进来的 [LifecycleWidget] 子节点跟着挂到同一宿主。
     * 普通 [Widget] 没有生命周期，忽略。
     */
    override fun onChildAdded(child: Widget) {
        val owner = host ?: return
        if (child is LifecycleWidget) child.attach(owner)
    }

    /**
     * 子 [LifecycleWidget] 被移出父子关系时，让它离开宿主并走一次 [onDestroy]。
     */
    override fun onChildRemoved(child: Widget) {
        if (child is LifecycleWidget) child.detach()
    }

    /** 对应宿主的 onCreate。初始化和只要创建一次的工作放这里。 */
    protected open fun onCreate(owner: LifecycleOwner) {}

    /** 宿主可见。适合开始轻量的界面相关工作。 */
    protected open fun onStart(owner: LifecycleOwner) {}

    /** 宿主来到前台。适合注册监听、开始刷新。 */
    protected open fun onResume(owner: LifecycleOwner) {}

    /** 宿主离开前台。适合暂停刷新、取消短生命周期的监听。 */
    protected open fun onPause(owner: LifecycleOwner) {}

    /** 宿主不可见。 */
    protected open fun onStop(owner: LifecycleOwner) {}

    /**
     * 宿主销毁，或本 Widget 被提前 [detach]。
     *
     * 两种情况都会进来一次，清理监听、协程和引用放这里即可。
     * 不要在这里再调 [detach] 去拆子 Widget：宿主销毁时子节点还会收到自己的回调。
     */
    protected open fun onDestroy(owner: LifecycleOwner) {}
}
