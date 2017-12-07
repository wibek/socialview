package com.hendraanggrian.socialview

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.Spannable
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.method.LinkMovementMethod.getInstance
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import android.widget.TextView.BufferType.SPANNABLE

class SocialViewImpl(val view: TextView, attrs: AttributeSet?) : SocialView {

    companion object {
        private const val FLAG_HASHTAG: Int = 1
        private const val FLAG_MENTION: Int = 2
        private const val FLAG_HYPERLINK: Int = 4
    }

    private val mTextWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (count > 0 && start > 0) when (s[start - 1]) {
                '#' -> {
                    mHashtagEditing = true
                    mMentionEditing = false
                }
                '@' -> {
                    mHashtagEditing = false
                    mMentionEditing = true
                }
                else -> when {
                    !Character.isLetterOrDigit(s[start - 1]) -> {
                        mHashtagEditing = false
                        mMentionEditing = false
                    }
                    mHashtagWatcher != null && mHashtagEditing -> mHashtagWatcher!!.invoke(this@SocialViewImpl, s.substring(indexOfPreviousNonLetterDigit(s, 0, start - 1) + 1, start))
                    mMentionWatcher != null && mMentionEditing -> mMentionWatcher!!.invoke(this@SocialViewImpl, s.substring(indexOfPreviousNonLetterDigit(s, 0, start - 1) + 1, start))
                }
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            // triggered when text is added
            if (s.isEmpty()) return
            colorize()
            if (start < s.length) {
                if (start + count - 1 < 0) return
                when (s[start + count - 1]) {
                    '#' -> {
                        mHashtagEditing = true
                        mMentionEditing = false
                    }
                    '@' -> {
                        mHashtagEditing = false
                        mMentionEditing = true
                    }
                    else -> when {
                        !Character.isLetterOrDigit(s[start]) -> {
                            mHashtagEditing = false
                            mMentionEditing = false
                        }
                        mHashtagWatcher != null && mHashtagEditing -> mHashtagWatcher!!.invoke(this@SocialViewImpl, s.substring(indexOfPreviousNonLetterDigit(s, 0, start) + 1, start + count))
                        mMentionWatcher != null && mMentionEditing -> mMentionWatcher!!.invoke(this@SocialViewImpl, s.substring(indexOfPreviousNonLetterDigit(s, 0, start) + 1, start + count))
                    }
                }
            }
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    private var mFlags: Int
    private var mHashtagColor: ColorStateList
    private var mMentionColor: ColorStateList
    private var mHyperlinkColor: ColorStateList
    private var mHashtagListener: ((SocialView, String) -> Unit)? = null
    private var mMentionListener: ((SocialView, String) -> Unit)? = null
    private var mHyperlinkListener: ((SocialView, String) -> Unit)? = null
    private var mHashtagWatcher: ((SocialView, String) -> Unit)? = null
    private var mMentionWatcher: ((SocialView, String) -> Unit)? = null
    private var mHashtagEditing: Boolean = false
    private var mMentionEditing: Boolean = false

    init {
        view.addTextChangedListener(mTextWatcher)
        view.setText(view.text, SPANNABLE)
        val a = view.context.obtainStyledAttributes(attrs, R.styleable.SocialView, R.attr.socialViewStyle, R.style.Widget_SocialView)
        mFlags = a.getInteger(R.styleable.SocialView_socialFlags, FLAG_HASHTAG or FLAG_MENTION or FLAG_HYPERLINK)
        mHashtagColor = a.getColorStateList(R.styleable.SocialView_hashtagColor)
        mMentionColor = a.getColorStateList(R.styleable.SocialView_mentionColor)
        mHyperlinkColor = a.getColorStateList(R.styleable.SocialView_hyperlinkColor)
        a.recycle()
        colorize()
    }

    override fun getContext(): Context = view.context

    override fun getText(): CharSequence = view.text

    override var isHashtagEnabled: Boolean
        get() = mFlags or FLAG_HASHTAG == mFlags
        set(enabled) {
            mFlags = when {
                enabled -> mFlags or FLAG_HASHTAG
                else -> mFlags and FLAG_HASHTAG.inv()
            }
            colorize()
        }

    override var isMentionEnabled: Boolean
        get() = mFlags or FLAG_MENTION == mFlags
        set(enabled) {
            mFlags = when {
                enabled -> mFlags or FLAG_MENTION
                else -> mFlags and FLAG_MENTION.inv()
            }
            colorize()
        }

    override var isHyperlinkEnabled: Boolean
        get() = mFlags or FLAG_HYPERLINK == mFlags
        set(enabled) {
            mFlags = when {
                enabled -> mFlags or FLAG_HYPERLINK
                else -> mFlags and FLAG_HYPERLINK.inv()
            }
            colorize()
        }

    override var hashtagColor: ColorStateList
        get() = mHashtagColor
        set(colorStateList) {
            mHashtagColor = colorStateList
            colorize()
        }

    override var mentionColor: ColorStateList
        get() = mMentionColor
        set(colorStateList) {
            mMentionColor = colorStateList
            colorize()
        }

    override var hyperlinkColor: ColorStateList
        get() = mHyperlinkColor
        set(colorStateList) {
            mHyperlinkColor = colorStateList
            colorize()
        }

    override fun colorize() {
        val spannable = view.text
        check(spannable is Spannable, { "Attached text is not a Spannable, add TextView.BufferType.SPANNABLE when setting text to this TextView." })
        spannable as Spannable
        spannable.removeSpans(*spannable.getSpans(CharacterStyle::class.java))
        if (isHashtagEnabled) spannable.span(SocialView.HASHTAG_PATTERN, {
            mHashtagListener?.newClickableSpan(it, mHashtagColor) ?: ForegroundColorSpan(mHashtagColor.defaultColor)
        })
        if (isMentionEnabled) spannable.span(SocialView.MENTION_PATTERN, {
            mMentionListener?.newClickableSpan(it, mMentionColor) ?: ForegroundColorSpan(mMentionColor.defaultColor)
        })
        if (isHyperlinkEnabled) spannable.span(SocialView.HYPERLINK_PATTERN, {
            mHyperlinkListener?.newClickableSpan(it, mHyperlinkColor, true) ?: object : ForegroundColorSpan(mMentionColor.defaultColor) {
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                }
            }
        })
    }

    override fun setOnHashtagClickListener(listener: ((view: SocialView, String) -> Unit)?) {
        view.setLinkMovementMethodIfNotAlready()
        mHashtagListener = listener
        colorize()
    }

    override fun setOnMentionClickListener(listener: ((view: SocialView, String) -> Unit)?) {
        view.setLinkMovementMethodIfNotAlready()
        mMentionListener = listener
        colorize()
    }

    override fun setOnHyperlinkClickListener(listener: ((view: SocialView, String) -> Unit)?) {
        view.setLinkMovementMethodIfNotAlready()
        mHyperlinkListener = listener
        colorize()
    }

    override fun setHashtagTextChangedListener(watcher: ((view: SocialView, String) -> Unit)?) {
        mHashtagWatcher = watcher
    }

    override fun setMentionTextChangedListener(watcher: ((view: SocialView, String) -> Unit)?) {
        mMentionWatcher = watcher
    }

    private fun indexOfNextNonLetterDigit(text: CharSequence, start: Int): Int = (start + 1 until text.length).firstOrNull { !Character.isLetterOrDigit(text[it]) } ?: text.length

    private fun indexOfPreviousNonLetterDigit(text: CharSequence, start: Int, end: Int): Int = (end downTo start + 1).firstOrNull { !Character.isLetterOrDigit(text[it]) } ?: start

    private fun TextView.setLinkMovementMethodIfNotAlready() {
        if (movementMethod == null || movementMethod !is LinkMovementMethod) movementMethod = getInstance()
    }

    private fun ((SocialView, String) -> Unit).newClickableSpan(s: String, color: ColorStateList, underline: Boolean = false): CharacterStyle = object : ClickableSpan() {
        override fun onClick(widget: View) = invoke(this@SocialViewImpl, s)
        override fun updateDrawState(ds: TextPaint) {
            ds.color = color.defaultColor
            ds.isUnderlineText = underline
        }
    }
}