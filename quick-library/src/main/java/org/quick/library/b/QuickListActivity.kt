package org.quick.library.b

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.Size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.app_base_list.*
import kotlinx.android.synthetic.main.app_include_no_msg.*
import org.quick.component.Constant
import org.quick.component.QuickAdapter
import org.quick.component.QuickViewHolder
import org.quick.component.callback.OnClickListener2
import org.quick.component.http.HttpService
import org.quick.component.http.callback.Callback
import org.quick.component.http.callback.ClassCallback
import org.quick.component.utils.GsonUtils
import org.quick.component.utils.check.CheckUtils
import org.quick.component.widget.QRecyclerView
import org.quick.library.R
import org.quick.library.model.BaseModel
import java.util.*

/**
 * Created by work on 2017/8/10.
 *
 * @author chris zou
 * @mail chrisSpringSmell@gmail.com
 *
 * @param M 数据模型，使用GSON解析JSON
 * @param MC 实际数据列表item
 */

abstract class QuickListActivity<M, MC> : BaseActivity(), QRecyclerView.OnRefreshListener {
    enum class ErrorType(var value: Int = 0) {
        /**
         * 没有数据
         */
        NO_MSG(-0x1),
        /**
         * 网络问题
         */
        NET_WORK(-0x2),
        /**
         * 服务器问题
         */
        SERVICE(-0x3),
        /**
         * 未登录
         */
        NO_LOGIN(-0x4),
        /**
         * 数据异常
         */
        DATA(-0x5),
        /**
         * 其他问题-自定义
         */
        OTHER(-0x3),
        NORMAL(0x1);
    }

    companion object {
        /**
         * 分页关键字
         */
        const val PAGER_NUMBER_KEY = "page"
        const val PAGER_COUNT_KEY = "num"
        const val PAGER_FIRST_NUMBER = 1
        const val PAGER_COUNT = 10
    }

    private var onTabSelectedListener: TabLayout.OnTabSelectedListener? = null
    private var isDefaultNoMsgLayout = true
    private var tabs: Array<String>? = null
    private val adapter = Adapter()
    private val params = HashMap<String, String>()

    var pageNumber = 1
    var onRequestListener: OnRequestListener? = null
    var errorType = ErrorType.NORMAL

    open val noMsgLayoutRes: Int
        @LayoutRes
        get() = R.layout.app_include_no_msg

    open val noMsgLayout: View
        get() {
            isDefaultNoMsgLayout = true
            return LayoutInflater.from(activity).inflate(noMsgLayoutRes, null)
        }

    private val isPullRefresh: Boolean
        get() = pageNumber <= 2

    abstract val isPullRefreshEnable: Boolean

    abstract val isLoadMoreEnable: Boolean

    override fun onInit() {

    }

    override fun onInitLayout() {
        recyclerView.setLayoutManager(onResultLayoutManager())
        recyclerView.setAdapter(onResultAdapter())
        recyclerView.setColorSchemeColors(*onResultRefreshColors())
        recyclerView.setRefreshListener(isPullRefreshEnable, isLoadMoreEnable, this)
    }

    override fun onBindListener() {
        if (isDefaultNoMsgLayout)
            refreshBtn.setOnClickListener(object : OnClickListener2() {
                override fun onClick2(view: View) {
                    onRefreshClick(errorType)
                }
            })
    }

    override fun onResultLayoutResId(): Int = R.layout.app_base_list

    fun setupTab(@Size(min = 1) vararg tabs: String) {
        setupTab(null, *tabs)
    }

    fun setupTab(onTabSelectedListener: TabLayout.OnTabSelectedListener?, @Size(min = 1) vararg tabs: String) {
        setupTab(onTabSelectedListener, -1, *tabs)
    }

    /**
     * 安装顶部TabLayout
     *
     * @param tabs
     */
    fun setupTab(
        onTabSelectedListener: TabLayout.OnTabSelectedListener?,
        selectorPosition: Int, @Size(min = 1) vararg tabs: String
    ): TabLayout {
        @Suppress("UNCHECKED_CAST")
        this.tabs = tabs as Array<String>
        tabLayout.visibility = View.VISIBLE
        if (this.onTabSelectedListener != null) tabLayout.removeOnTabSelectedListener(this.onTabSelectedListener!!)
        if (onTabSelectedListener != null) {
            this.onTabSelectedListener = onTabSelectedListener
            tabLayout.addOnTabSelectedListener(this.onTabSelectedListener!!)
        }

        if (tabLayout.tabCount > 0) {
            var i = 0
            while (i < tabLayout.tabCount && i < tabs.size) {
                tabLayout.getTabAt(i)!!.text = tabs[i]
                i++
            }
        } else tabs.forEach {
            tabLayout.addTab(tabLayout.newTab().setText(it))
        }
        if (selectorPosition > 0 && selectorPosition < tabs.size)
            Objects.requireNonNull<TabLayout.Tab>(tabLayout!!.getTabAt(selectorPosition)).select()
        return tabLayout
    }

    fun setupDividerLine(drawable: Drawable, padding: Float = -1f) {
        recyclerView.setupDividerLine(drawable, padding)
    }

    fun setupDividerLine(color: Int, padding: Float = -1f) {
        recyclerView.setupDividerLine(color, padding)
    }

    fun refreshComplete() {
        recyclerView.refreshComplete()
    }

    fun loadMoreComplete() {
        recyclerView.loadMoreComplete()
    }

    fun addFooterView(view: View) {
        footerContainer.addView(view)
    }

    fun addHeadeViewRv(@Size(min = 1) vararg views: View) {
        recyclerView.addHeaderView(*views)
    }

    fun addFooterView(@Size(min = 1) vararg views: View) {
        recyclerView.addFooterView(*views)
    }

    override fun onRefresh() {
        if (!CheckUtils.isNetWorkAvailable())
            dataHas(false, QuickListActivity.ErrorType.NET_WORK)
        else if (!TextUtils.isEmpty(onResultUrl())) {
            pageNumber = QuickListActivity.PAGER_FIRST_NUMBER
            requestData()
        }
    }

    override fun onLoading() {
        if (!TextUtils.isEmpty(onResultUrl()))
            requestData()
    }

    open fun requestData() {
        params.clear()
        onResultParams(params)
        if (!params.containsKey(QuickListActivity.PAGER_NUMBER_KEY))
            params[QuickListActivity.PAGER_NUMBER_KEY] = pageNumber.toString()
        if (!params.containsKey(QuickListActivity.PAGER_COUNT_KEY))
            params[QuickListActivity.PAGER_COUNT_KEY] = QuickListActivity.PAGER_COUNT.toString()

        HttpService.Builder(onResultUrl()).addParams(params).post().enqueue(object : Callback<String>() {

            override fun onFailure(e: Throwable, isNetworkError: Boolean) {
                dataHas(false, QuickListActivity.ErrorType.SERVICE)
                onRequestListener?.onError("", isPullRefresh, errorType)
            }

            override fun onResponse(value: String?) {
                checkData(value)
                pageNumber++
            }

            override fun onEnd() {
                if (isPullRefresh && isPullRefreshEnable)
                    recyclerView.refreshComplete()
                else if (isLoadMoreEnable)
                    recyclerView.loadMoreComplete()
                onRequestListener?.onEnd()
            }
        })
    }

    /**
     * 消息统一处理
     */
    private fun checkData(value: String?) {
        val model = GsonUtils.parseFromJson(value, BaseModel::class.java)
        if (model != null) {
            when (model.status) {
                /*成功*/
                Constant.APP_SUCCESS_TAG -> {
                    val realModel =
                        GsonUtils.parseFromJson(value, ClassCallback.getTClass(this@QuickListActivity::class.java)) as M
                    if (realModel != null) {
                        recyclerView.isNoMore = false
                        dataHas(true)
                        if (isPullRefresh) onPullRefreshSuccess(realModel) else onLoadMoreSuccess(realModel)
                    } else {
                        if (isPullRefresh)
                            dataHas(false, QuickListActivity.ErrorType.DATA)
                        else
                            showToast(getString(R.string.errorDataHint))
                        onRequestListener?.onError(value!!, isPullRefresh, errorType)
                    }
                }
                /*没有消息*/
                Constant.APP_ERROR_MSG_N0 -> {
                    dataHas(false, QuickListActivity.ErrorType.NO_MSG)
                    showToast(model.msg)
                    onRequestListener?.onError(value!!, isPullRefresh, errorType)
                }
                /*没有更多消息*/
                Constant.APP_ERROR_MSG_N0_MORE -> {
                    recyclerView.isNoMore = true
                    showToast("没有更多消息啦")
                    onRequestListener?.onError(value!!, isPullRefresh, errorType)
                }
                /*未登录*/
                Constant.APP_ERROR_NO_LOGIN -> {
                    dataHas(false, QuickListActivity.ErrorType.NO_LOGIN)
                    onRequestListener?.onError(value!!, isPullRefresh, errorType)
                }
                /*其他异常*/
                else -> {
                    dataHas(false, QuickListActivity.ErrorType.SERVICE)
                    onRequestListener?.onError(value!!, isPullRefresh, errorType)
                }
            }
        } else {
            dataHas(false, QuickListActivity.ErrorType.DATA)
            onRequestListener?.onError(value!!, isPullRefresh, errorType)
        }
    }

    fun dataNoMore(isNoMore: Boolean) {
        recyclerView.isNoMore = isNoMore
    }

    /**
     * 设置是否有数据
     *
     * @param isHasData
     */
    fun dataHas(isHasData: Boolean) {
        dataHas(isHasData, ErrorType.NO_MSG)
    }

    /**
     * 设置是否有数据
     *
     * @param isHasData
     */
    @Synchronized
    fun dataHas(isHasData: Boolean, type: ErrorType) {
        if (isHasData) {//有
            errorType = ErrorType.NORMAL
            if (noMsgContainer.visibility == View.VISIBLE) noMsgContainer.visibility = View.GONE
            if (recyclerView.visibility == View.GONE) recyclerView.visibility = View.VISIBLE
        } else {//无
            if (noMsgContainer.visibility == View.GONE) noMsgContainer.visibility = View.VISIBLE
            if (recyclerView.visibility == View.VISIBLE) recyclerView.visibility = View.GONE
            setHintErrorStyle(type)
        }
    }

    private fun setHintErrorStyle(type: ErrorType) {
        this.errorType = type
        if (isDefaultNoMsgLayout) {
            when (type) {
                ErrorType.NO_MSG -> {
                    hintErrorIv.setImageResource(onResultErrorNoMsgIcon())
                    hintErrorTv.text = onResultErrorNoMsgTxt()
                    refreshBtn.visibility = View.GONE
                    refreshBtn.text = onResultErrorBtnTxt()
                }
                ErrorType.NET_WORK -> {
                    hintErrorIv.setImageResource(onResultErrorNetWorkIcon())
                    hintErrorTv.text = onResultErrorNetWorkTxt()
                    refreshBtn.visibility = View.VISIBLE
                    refreshBtn.text = onResultErrorBtnTxt()
                }
                ErrorType.SERVICE -> {
                    hintErrorIv.setImageResource(onResultErrorServiceIcon())
                    hintErrorTv.text = onResultErrorServiceTxt()
                    refreshBtn.visibility = View.VISIBLE
                    refreshBtn.text = onResultErrorBtnTxt()
                }
                ErrorType.OTHER -> {
                    hintErrorIv.setImageResource(onResultErrorOtherIcon())
                    hintErrorTv.text = onResultErrorOtherTxt()
                    refreshBtn.visibility = View.VISIBLE
                }
                else -> {
                }
            }
        }
    }

    /**
     * 刷新按钮的点击事件
     */
    open fun onRefreshClick(errorType: QuickListActivity.ErrorType) {
        onRefresh()
    }


    fun getAdapter(): Adapter = adapter

    fun setDataList(dataList: MutableList<MC>) {
        getAdapter().setDataList(dataList)
    }

    fun addDataList(dataList: MutableList<MC>) {
        getAdapter().addDataList(dataList)
    }

    fun addData(m: MC) {
        getAdapter().addData(m)
    }

    fun getDataList(): MutableList<MC> = getAdapter().getDataList()

    fun getCount() = getDataList().size

    fun getItem(position: Int): MC = getAdapter().getItem(position)

    fun remove(position: Int) {
        getAdapter().remove(position)
    }

    fun remove(m: MC) {
        getAdapter().remove(m)
    }

    fun removeAll() {
        getAdapter().removeAll()
    }

    fun setOnClickListener(
        onClickListener: ((view: View, viewHolder: QuickViewHolder, position: Int, itemData: MC) -> Unit),
        @Size(min = 1) vararg resId: Int
    ) {
        getAdapter().setOnClickListener(onClickListener, *resId)
    }

    fun setOnCheckedChangedListener(
        onCheckedChangedListener: ((view: View, viewHolder: QuickViewHolder, isChecked: Boolean, position: Int, itemData: MC) -> Unit), @Size(
            min = 1
        ) vararg resId: Int
    ) {
        getAdapter().setOnCheckedChangedListener(onCheckedChangedListener, *resId)
    }

    fun setOnItemClickListener(onItemClickListener: ((view: View, viewHolder: QuickViewHolder, position: Int, itemData: MC) -> Unit)) {
        getAdapter().setOnItemClickListener(onItemClickListener)
    }

    fun setOnItemLongClickListener(onItemLongClickListener: ((view: View, viewHolder: QuickViewHolder, position: Int, itemData: MC) -> Boolean)) {
        getAdapter().setOnItemLongClickListener(onItemLongClickListener)
    }

    /**
     * 返回网络错误图片
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorNetWorkIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorNetWorkTxt(): String = getString(R.string.errorNetWorkHint)

    /**
     * 返回没有数据的图片
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorNoMsgIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorNoMsgTxt(): String = getString(R.string.errorNoMsgHint)

    /**
     * 返回服务器错误的图片
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorServiceIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorServiceTxt(): String = getString(R.string.errorServiceHint)

    /**
     * 返回服务器错误的图片
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorOtherIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorOtherTxt(): String = getString(R.string.errorOtherHint)

    /**
     * 未登录
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorNoLoginIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorNoLoginTxt(): String = getString(R.string.errorNoLoginHint)

    /**
     * 数据异常
     *
     * @return
     */
    @DrawableRes
    open fun onResultErrorDataIcon(): Int = R.drawable.ic_broken_image_gray_24dp

    open fun onResultErrorDataTxt(): String = getString(R.string.errorDataHint)
    /**
     * 网络出错时的文字
     *
     * @return
     */
    open fun onResultErrorBtnTxt(): String = getString(R.string.refresh)

    open fun onResultLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(activity)

    open fun onResultRefreshColors(): IntArray = intArrayOf(Color.BLACK)

    open fun onResultAdapter(): QuickAdapter<*> = adapter

    abstract fun onResultUrl(): String

    abstract fun onResultParams(params: MutableMap<String, String>)

    /**
     * 数据请求成功
     *
     * @param model 数据model
     * @return
     */
    abstract fun onPullRefreshSuccess(model: M)

    /**
     * 数据请求成功
     *
     * @param model 数据model
     * @return
     */
    abstract fun onLoadMoreSuccess(model: M)

    abstract fun onResultItemResId(viewType: Int): Int

    abstract fun onBindData(holder: QuickViewHolder, position: Int, itemData: MC, viewType: Int)

    open fun getItemViewType(position: Int): Int = -1

    open fun onResultItemMargin(position: Int): Float = 0f

    open fun onResultItemMarginLeft(position: Int): Float {
        return if (!getAdapter().isVertically()) {
            when (position) {
                0 -> {
                    onResultItemMargin(position)
                }
                else -> onResultItemMargin(position) / 2
            }
        } else onResultItemMargin(position)
    }

    open fun onResultItemMarginRight(position: Int): Float {
        return if (!getAdapter().isVertically()) {
            when (position) {
                getAdapter().itemCount - 1 -> {
                    onResultItemMargin(position)
                }
                else -> onResultItemMargin(position) / 2
            }
        } else onResultItemMargin(position)
    }

    open fun onResultItemMarginTop(position: Int): Float {
        return if (getAdapter().isVertically()) {
            when (position) {
                0 -> {
                    onResultItemMargin(position)
                }
                else -> onResultItemMargin(position) / 2
            }
        } else onResultItemMargin(position)
    }

    open fun onResultItemMarginBottom(position: Int): Float {
        return if (getAdapter().isVertically()) {
            when (position) {
                getAdapter().itemCount - 1 -> {
                    onResultItemMargin(position)
                }
                else -> onResultItemMargin(position) / 2
            }
        } else onResultItemMargin(position)
    }

    open fun onResultItemPadding(position: Int): Float = 0f

    open fun onResultItemPaddingLeft(position: Int): Float {
        return if (!getAdapter().isVertically()) {
            when (position) {
                0 -> {
                    onResultItemPadding(position)
                }
                else -> onResultItemPadding(position) / 2
            }
        } else onResultItemPadding(position)
    }

    open fun onResultItemPaddingRight(position: Int): Float {
        return if (!getAdapter().isVertically()) {
            when (position) {
                getAdapter().itemCount - 1 -> {
                    onResultItemPadding(position)
                }
                else -> onResultItemPadding(position) / 2
            }
        } else onResultItemPadding(position)
    }

    open fun onResultItemPaddingTop(position: Int): Float {
        return if (getAdapter().isVertically()) {
            when (position) {
                0 -> {
                    onResultItemPadding(position)
                }
                else -> onResultItemPadding(position) / 2
            }
        } else onResultItemPadding(position)
    }

    open fun onResultItemPaddingBottom(position: Int): Float {
        return if (getAdapter().isVertically()) {
            when (position) {
                getAdapter().itemCount - 1 -> {
                    onResultItemPadding(position)
                }
                else -> onResultItemPadding(position) / 2
            }
        } else onResultItemPadding(position)
    }

    inner class Adapter : QuickAdapter<MC>() {
        override fun onResultItemResId(viewType: Int): Int = this@QuickListActivity.onResultItemResId(viewType)

        override fun onBindData(holder: QuickViewHolder, position: Int, itemData: MC, viewType: Int) {
            this@QuickListActivity.onBindData(holder, position, itemData, viewType)
        }

        override fun getItemViewType(position: Int): Int {
            return this@QuickListActivity.getItemViewType(super.getItemViewType(position))
        }

        override fun onResultItemMargin(position: Int): Float = this@QuickListActivity.onResultItemMargin(position)

        override fun onResultItemMarginLeft(position: Int): Float =
            this@QuickListActivity.onResultItemMarginLeft(position)

        override fun onResultItemMarginRight(position: Int): Float {
            return this@QuickListActivity.onResultItemMarginRight(position)
        }

        override fun onResultItemMarginTop(position: Int): Float {
            return this@QuickListActivity.onResultItemMarginTop(position)
        }

        override fun onResultItemMarginBottom(position: Int): Float {
            return this@QuickListActivity.onResultItemMarginBottom(position)
        }

        override fun onResultItemPadding(position: Int): Float {
            return this@QuickListActivity.onResultItemPadding(position)
        }

        override fun onResultItemPaddingLeft(position: Int): Float {
            return this@QuickListActivity.onResultItemPaddingLeft(position)
        }

        override fun onResultItemPaddingRight(position: Int): Float {
            return this@QuickListActivity.onResultItemPaddingRight(position)
        }

        override fun onResultItemPaddingTop(position: Int): Float {
            return this@QuickListActivity.onResultItemPaddingTop(position)
        }

        override fun onResultItemPaddingBottom(position: Int): Float {
            return this@QuickListActivity.onResultItemPaddingBottom(position)
        }
    }

    interface OnRequestListener {
        fun onEnd()
        fun onError(jsonData: String, isPullRefresh: Boolean, errorType: ErrorType)
    }
}
