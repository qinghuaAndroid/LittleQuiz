# XML Widget Framework

本项目的 `widget` 包把一块 XML 布局及其事件处理封装为可组合的 Kotlin 对象；它不依赖 Compose。

## 三种 Widget

- `Widget`：没有宿主生命周期。适合纯视图、点击事件和一次性渲染。
- `LifecycleWidget`：通过 `attach(owner)` 订阅 Activity 或 Fragment 的生命周期。
- `RecyclerItemWidget`：由 `WidgetAdapter` 驱动，使用 RecyclerView 的绑定、进出窗口和回收事件；**不要**同时把它绑定到 `LifecycleOwner`。

## 使用约定

### 普通与生命周期 Widget

每个 Widget 提供一个 `root: View`，通常由 `LayoutInflater.inflate(..., parent, false)` 创建。`Widget` 只维护对象之间的父子关系，不会自动改变 XML 中的 View 层级。

```kotlin
class ProfileWidget(parent: ViewGroup) : LifecycleWidget() {
    override val root = LayoutInflater.from(parent.context)
        .inflate(R.layout.widget_profile, parent, false)

    override fun onStart(owner: LifecycleOwner) {
        // 开始观察或刷新 UI
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // 取消监听、协程及持有的引用
    }
}

val widget = ProfileWidget(binding.container)
widget.attach(this) // Activity
// Fragment 中必须使用 viewLifecycleOwner：widget.attach(viewLifecycleOwner)
widget.attachTo(binding.container)
```

当父 `LifecycleWidget` 已绑定宿主时，新加入的生命周期子节点会自动绑定同一个宿主；移除子节点会使其 `detach()`。一个子 Widget 只能属于一个父节点，需先从旧父节点移除后才能重新添加，避免同一实例收到重复生命周期或 RecyclerView 回调。

`detach()` 会立即执行一次 `onDestroy` 用于资源清理。正常的宿主销毁也会执行一次 `onDestroy`。不要在 `onDestroy` 中再次调用 `detach()`。

## RecyclerView Widget

继承 `WidgetAdapter`，仅实现 `onCreateWidget`、`getItemCount` 和数据更新；不要覆写 Adapter 的绑定、回收或窗口回调，因为基类会分发到 item Widget。

```kotlin
class UserAdapter : WidgetAdapter<UserItemWidget>() {
    override fun onCreateWidget(parent: ViewGroup, viewType: Int) = UserItemWidget(parent)
    override fun getItemCount() = users.size
}

class UserItemWidget(parent: ViewGroup) : RecyclerItemWidget() {
    override val root = LayoutInflater.from(parent.context)
        .inflate(R.layout.item_user, parent, false)

    override fun onBind(position: Int, payloads: List<Any>) {
        // 每次绑定都完整建立 UI 状态；payloads 非空时可做增量更新
    }

    override fun onViewAttached() { /* 启动仅在屏幕上需要的动画/曝光 */ }
    override fun onViewDetached() { /* 暂停动画等屏幕可见资源 */ }
    override fun onRecycle() { /* 取消图片请求、监听，清理临时状态 */ }
}
```

回调时序为：`onBind` → `onViewAttached`（可选）→ `onViewDetached`（可选）→ `onRecycle`。同一个 ViewHolder 可在未先回收的情况下再次绑定，框架会先为旧绑定执行完整回收再开始新绑定。`onRecycle` 只会对已绑定的 Widget 执行一次，避免 RecyclerView 回收从未绑定的 Holder 时产生错误清理。

## 审查后的修复

- 防止同一个子 Widget 同时被多个父 Widget 持有，从而重复接收生命周期或列表回调。
- 将 RecyclerView 的“重新绑定”视为一次新的绑定周期：若旧绑定仍在，先分发回收，防止图片请求、订阅或位置状态泄漏到新条目。
- 忽略从未绑定 Widget 的回收回调，避免执行与不存在的数据绑定相对应的清理代码。

## 注意事项

- 所有 Widget、View 和 RecyclerView 回调都必须在主线程使用。
- Fragment 必须绑定 `viewLifecycleOwner`，不能绑定 Fragment 自身，否则在 `onDestroyView` 后可能继续持有旧 View。
- `onBind` 不应启动没有对应取消逻辑的任务；用 `onRecycle` 取消与当前数据项关联的请求，用 `onViewDetached` 停止仅在可见时需要的工作。
- `attachTo` 会将 View 从原父容器移除后再添加；仅在确实需要移动该 View 时调用。
