package com.bl_lia.kirakiratter.presentation.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bl_lia.kirakiratter.App
import com.bl_lia.kirakiratter.R
import com.bl_lia.kirakiratter.presentation.activity.TimelineActivity
import com.bl_lia.kirakiratter.presentation.internal.di.component.DaggerStatusComponent
import com.bl_lia.kirakiratter.presentation.internal.di.component.StatusComponent
import com.bl_lia.kirakiratter.presentation.internal.di.module.FragmentModule
import com.bl_lia.kirakiratter.presentation.presenter.KatsuPresenter
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_katsu.*
import javax.inject.Inject

class KatsuFragment : Fragment() {

    companion object {
        fun newInstance(accountName: String? = null, replyStatusId: Int? = null, sharedText: String? = null, sharedImages: ArrayList<Uri> = arrayListOf()): KatsuFragment =
                KatsuFragment().also { fragment ->
                    val bundle = Bundle().apply {
                        if (!accountName.isNullOrEmpty()) putString(PARAM_ACCOUNT_NAME, accountName)
                        if (replyStatusId != null && replyStatusId > -1) putInt(PARAM_REPLY_STATUS_ID, replyStatusId)
                        putString(PARAM_SHARED_TEXT, sharedText)
                        putParcelableArrayList(PARAM_SHARED_IMAGE, sharedImages)
                    }
                    fragment.arguments = bundle
                }

        private val REQUEST_PICK_IMAGE = 1
        private val PARAM_ACCOUNT_NAME = "param_account_name"
        private val PARAM_REPLY_STATUS_ID = "param_reply_status_id"
        private val PARAM_SHARED_TEXT = "param_shared_text"
        private val PARAM_SHARED_IMAGE = "param_shared_image"
    }

    @Inject
    lateinit var presenter: KatsuPresenter

    val mediaUris = mutableListOf<Uri>()
    val attachImageViews by lazy { listOf(attach_image_1, attach_image_2, attach_image_3, attach_image_4) }

    private val component: StatusComponent by lazy {
        DaggerStatusComponent.builder()
                .applicationComponent((activity.application as App).component)
                .fragmentModule(FragmentModule(this))
                .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater?.inflate(R.layout.fragment_katsu, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_katsu.setOnClickListener {
            val header = content_warning_edittext.text.toString()
            val body = katsu_content_body.text.toString()
            val replyTo: Int? =
                    if (arguments.containsKey(PARAM_REPLY_STATUS_ID)) {
                        arguments.getInt(PARAM_REPLY_STATUS_ID)
                    } else {
                        null
                    }

            if (body.isNotEmpty()) {
                button_katsu.isEnabled = false
                presenter.post(
                        text = body,
                        warning = header,
                        attachment = mediaUris,
                        inReplyToId = replyTo
                ).subscribe { status, error ->
                    button_katsu.isEnabled = true
                    if (error != null) {
                        showError(error)
                        return@subscribe
                    }

                    val intent = Intent(activity, TimelineActivity::class.java).apply {
                        setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    activity?.finish()
                }
            }
        }

        switch_content_warning.setOnCheckedChangeListener { button, checked ->
            content_warning_textinput.visibility = if (checked) View.VISIBLE else View.GONE
        }

        attachImageViews.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                if (index == mediaUris.size) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        setType("image/*")
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(intent, REQUEST_PICK_IMAGE)
                }
                else if(index < mediaUris.size) {
                    AlertDialog.Builder(activity)
                            .setTitle(R.string.cancel_image_dialog_title)
                            .setMessage(R.string.cancel_image_dialog_message)
                            .setPositiveButton(android.R.string.yes) { dialog, id ->
                                removeImageAt(index)
                                setButtonVisibility()
                            }
                            .setNegativeButton(android.R.string.no, null)
                            .show()
                }
            }
        }

        katsu_content_body.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                setButtonVisibility()
            }
        })

        if (arguments.containsKey(PARAM_ACCOUNT_NAME)) {
            val accountText = "@%s ".format(arguments.getString(PARAM_ACCOUNT_NAME))
            katsu_content_body.setText(accountText, TextView.BufferType.NORMAL)
            katsu_content_body.setSelection(accountText.length)
        }

        setHint()

        if (arguments.containsKey(PARAM_SHARED_TEXT)) {
            arguments.getString(PARAM_SHARED_TEXT)?.let { sharedText ->
                katsu_content_body.setText("%s ".format(sharedText))
                katsu_content_body.setSelection(sharedText.length + 1)
            }
        }

        if (arguments.containsKey(PARAM_SHARED_IMAGE)) {
            arguments.getParcelableArrayList<Uri>(PARAM_SHARED_IMAGE)?.forEach { sharedImage ->
                attachImage(sharedImage)
            }
        }

        setButtonVisibility()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK
                && requestCode == REQUEST_PICK_IMAGE) {
            data?.let {
                data.clipData?.let {
                    (0..data.clipData.itemCount - 1).map { data.clipData.getItemAt(it) }.forEach { item ->
                        attachImage(item.uri)
                    }
                }
                data.data?.let {
                    attachImage(data.data)
                }
            }
            setButtonVisibility()
        }
    }

    private fun setHint() {
        val hintHeader = content_warning_textinput.hint
        content_warning_textinput.hint = null
        content_warning_textinput.editText?.hint = hintHeader

        val hintContent = layout_content_body.hint
        layout_content_body.hint = null
        layout_content_body.editText?.hint = hintContent
    }

    private fun showError(error: Throwable) {
        Snackbar.make(layout_content, error.localizedMessage, Snackbar.LENGTH_LONG).show()
    }

    private fun setButtonVisibility() {
        val body = katsu_content_body.text.toString()

        button_katsu.isEnabled = body.isNotEmpty() || mediaUris.size > 0

        attachImageViews.forEachIndexed { index, imageView ->
            imageView.visibility = if(mediaUris.size >= index) View.VISIBLE else View.GONE
        }
    }

    private fun attachImage(imageUri: Uri) {
        if(mediaUris.size >= attachImageViews.size) {
            Snackbar.make(layout_content, R.string.post_image_over_max, Snackbar.LENGTH_LONG).show()
            return
        }

        mediaUris.add(imageUri)
        val imageView = attachImageViews[mediaUris.size - 1]
        imageView.setBackgroundResource(0)
        Picasso.with(activity)
                .load(imageUri)
                .resize(resources.getDimensionPixelSize(R.dimen.katsu_image_width),
                        resources.getDimensionPixelSize(R.dimen.katsu_image_height))
                .centerInside()
                .onlyScaleDown()
                .into(imageView)
    }

    private fun removeImageAt(index:Int) {
        mediaUris.removeAt(index)

        attachImageViews.subList(index, attachImageViews.size - 1).forEachIndexed { index2, imageView2 ->
            imageView2.setImageDrawable(attachImageViews[index + index2 + 1].drawable)
        }
        attachImageViews[mediaUris.size].apply {
            setImageDrawable(null)
            setBackgroundResource(R.drawable.ic_camera_alt_black_24px)
        }
    }
}