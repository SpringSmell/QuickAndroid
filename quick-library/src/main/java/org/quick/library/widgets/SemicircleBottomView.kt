package org.quick.library.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import org.quick.library.R
import org.quick.component.utils.FormatUtils


/**
 * Created by work on 2017/4/20.
 * 底部圆形
 * @author chris zou
 * @mail chrisSpringSmell@gmail.com
 */

class SemicircleBottomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    internal var width: Int = 0
    internal var height: Int = 0
    lateinit var mPaint: Paint
    var radius: Float = 0.toFloat()
    var shadowDistance = 0f
    var shadowRadious = 0f
    var startColor: Int = 0
    var endColor: Int = 0
    var paddingHeight: Float = 0.toFloat()
    var isShadow = true

    init {
        init()
        val ta = context.obtainStyledAttributes(attrs, R.styleable.SemicircleBottomView)
        radius = ta.getDimension(R.styleable.SemicircleBottomView_svRadius, 0f)
        startColor = ta.getColor(R.styleable.SemicircleBottomView_svStartColor, Color.BLACK)
        endColor = ta.getColor(R.styleable.SemicircleBottomView_svEndColor, Color.BLACK)
        paddingHeight = ta.getDimension(R.styleable.SemicircleBottomView_svPaddingHeight, 0.0f)
        isShadow = ta.getBoolean(R.styleable.SemicircleBottomView_svIsShadow, true)
        shadowDistance = ta.getDimension(R.styleable.SemicircleBottomView_svShadowDistance, FormatUtils.formatDip2Px(10f))
        shadowRadious = ta.getDimension(R.styleable.SemicircleBottomView_svShadowRadius, FormatUtils.formatDip2Px(2f))
        ta.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        width = w
        height = h
        if (paddingHeight > h) {
            paddingHeight = h.toFloat()
        }
    }

    private fun init() {
        mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.color = Color.BLACK
    }

    fun setColors(vararg colors: Int) {
        startColor = colors[0]
        endColor = if (colors.size >= 2)
            colors[1]
        else
            startColor
        postInvalidate()
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        configPaint()
        drawLine(canvas)
    }

    private fun configPaint() {
        if (endColor == Color.BLACK) {
            mPaint.color = startColor
        } else {
            val colors = intArrayOf(startColor, endColor)
            val linearGradient = RadialGradient((getWidth() / 2).toFloat(), 0f, (height / 2).toFloat(), colors, null, Shader.TileMode.MIRROR)
            mPaint.shader = linearGradient
        }
        if (isShadow) {
            mPaint.setShadowLayer(shadowRadious, 0f, shadowDistance, Color.BLACK)
        }
    }

    private fun drawLine(canvas: Canvas?) {
        val path = Path()
        path.moveTo(radius, 0f)
        if (radius > 0) {
            path.quadTo(0f, 0f, 0f, radius)
        }
        path.lineTo(0f, height.toFloat() - paddingHeight)
        path.quadTo(width / 2.0f, height.toFloat(), width.toFloat(), height.toFloat() - paddingHeight)
//        pathCir.quadTo(width / 2.0f, height.toFloat() + paddingHeight, width.toFloat(), height.toFloat() - paddingHeight)
        path.lineTo(width.toFloat(), radius)
        if (radius > 0)
            path.quadTo(width.toFloat(), 0f, width.toFloat() - radius, 0f)
        path.lineTo(0f, 0f)
        canvas?.drawPath(path, mPaint)
    }
}
