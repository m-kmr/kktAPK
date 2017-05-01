package com.bl_lia.kirakiratter.presentation.fragment

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bl_lia.kirakiratter.App
import com.bl_lia.kirakiratter.R
import com.bl_lia.kirakiratter.domain.entity.Account
import com.bl_lia.kirakiratter.presentation.activity.KatsuActivity
import com.bl_lia.kirakiratter.presentation.adapter.account.AccountAdapter
import com.bl_lia.kirakiratter.presentation.internal.di.component.AccountFragmentComponent
import com.bl_lia.kirakiratter.presentation.internal.di.component.DaggerAccountFragmentComponent
import com.bl_lia.kirakiratter.presentation.presenter.AccountFragmentPresenter
import com.bl_lia.kirakiratter.presentation.scroll_listener.TimelineScrollListener
import kotlinx.android.synthetic.main.fragment_account.*
import javax.inject.Inject

class AccountFragment : Fragment() {

    companion object {
        fun newInstance(account: Account): AccountFragment =
                AccountFragment().also { fragment ->
                    fragment.arguments = Bundle().also { args ->
                        args.putSerializable("account", account)
                    }
                }
    }

    @Inject
    lateinit var presenter: AccountFragmentPresenter

    private var moreLoading: Boolean = false

    private val layoutManager: RecyclerView.LayoutManager by lazy {
        LinearLayoutManager(activity)
    }

    private val scrollListener: TimelineScrollListener by lazy {
        object : TimelineScrollListener(layoutManager as LinearLayoutManager) {
            override fun onLoadMore() {
                adapter.maxId?.let { maxId ->
                    if (moreLoading) return@let
                    moreLoading = true

                    presenter.fetchMoreStatus(account, maxId)
                            ?.doAfterTerminate { moreLoading = false }
                            ?.subscribe { list, error ->
                                if (error != null) {
                                    showError(error)
                                    return@subscribe
                                }

                                adapter.add(list)
                            }
                }
            }
        }
    }

    private val account: Account by lazy {
        arguments.getSerializable("account") as Account
    }

    private val adapter: AccountAdapter by lazy {
        AccountAdapter()
    }

    private val component: AccountFragmentComponent by lazy {
        DaggerAccountFragmentComponent.builder()
                .applicationComponent((activity.application as App).component)
                .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater?.inflate(R.layout.fragment_account, container, false)

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (list_status.layoutManager == null) {
            list_status.layoutManager = layoutManager
        }

        list_status.adapter = adapter
        list_status.addOnScrollListener(scrollListener)
        presenter.fetchStatus(account)
                .subscribe { list, error ->
                    if (error != null) {
                        showError(error)
                        return@subscribe
                    }

                    adapter.reset(list)
                }

        adapter.onClickReply.subscribe { status ->
            val target = status.reblog ?: status
            val intent = Intent(activity, KatsuActivity::class.java).apply {
                putExtra(KatsuActivity.INTENT_PARAM_REPLY_ACCOUNT_NAME, target.account?.userName)
                putExtra(KatsuActivity.INTENT_PARAM_REPLY_STATUS_ID, target.id)
            }
            startActivity(intent)
        }
        adapter.onClickReblog.subscribe { status ->
            val target = status.reblog ?: status
            presenter.reblog(target)
                    .subscribe { status, error ->
                        if (error != null) {
                            showError(error)
                            return@subscribe
                        }

                        val updateTarget = status.reblog ?: status
                        adapter.update(updateTarget)
                    }
        }
    }

    private fun showError(error: Throwable) {
        val messsage =
                if (error.localizedMessage.startsWith("HTTP 520")) {
                    resources.getString(R.string.error_message_5xx)
                } else {
                    error.localizedMessage
                }
        Snackbar.make(layout_content, messsage, Snackbar.LENGTH_LONG).show()
    }
}