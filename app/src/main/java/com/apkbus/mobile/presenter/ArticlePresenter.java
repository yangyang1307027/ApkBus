package com.apkbus.mobile.presenter;

import com.apkbus.mobile.apis.MobError;
import com.apkbus.mobile.bean.event.ScrollSignal;
import com.apkbus.mobile.constract.ArticleContract;
import com.apkbus.mobile.apis.LSubscriber;
import com.apkbus.mobile.apis.RxAPI;
import com.apkbus.mobile.bean.BeanWrapper;
import com.apkbus.mobile.bean.Bean;
import com.apkbus.mobile.utils.ACache;
import com.apkbus.mobile.utils.RxBus;
import com.apkbus.mobile.utils.SharedPreferencesHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by liyiheng on 16/9/19.
 */
public class ArticlePresenter implements ArticleContract.Presenter {
    private final ACache aCache;

    @Override
    public void subscribe() {
        // todo
    }

    @Override
    public void unSubscribe() {
        // todo
        //mView = null;

    }

    private ArticleContract.View mView;
    private int SECTION_INDEX;
    private CompositeSubscription mSubscriptions;

    public ArticlePresenter(ArticleContract.View view, int sectionIndex, CompositeSubscription s) {
        this.mView = view;
        this.SECTION_INDEX = sectionIndex;
        mSubscriptions = s;
        aCache = ACache.get(mView.getContext().getApplicationContext());
        Subscription subscription = RxBus
                .getInstance()
                .toSubscription(ScrollSignal.class, (ScrollSignal scrollSignal)
                        -> {
                    if (scrollSignal.tabPosition == SECTION_INDEX) {
                        mView.scroll2Top();
                    }
                });
        mSubscriptions.add(subscription);
    }

    private boolean firstTime = true;

    @Override
    public void initData() {
        final Gson gson = new Gson();
        RxAPI api = RxAPI.getInstance();
        Observable<BeanWrapper<Bean>> observer = null;
        switch (SECTION_INDEX) {
            case 0:
                observer = api.getPopularArticles();
                break;
            case 1:
                observer = api.getLatestArticles();
                break;
            case 2:
                observer = api.getAwsomeSource();
                break;
            case 3:
                observer = api.getWeeklyPopular();
                break;
            case 4:
                observer = api.getDemos();
                break;
        }

        // Load data from cache at the first time.
        if (firstTime) {
            firstTime = false;
            JSONObject jsonObject = aCache.getAsJSONObject("DATA" + SECTION_INDEX);
            if (jsonObject != null && observer != null) {
                BeanWrapper<Bean> data = gson.fromJson(jsonObject.toString(), new TypeToken<BeanWrapper<Bean>>() {
                }.getType());
                mView.updateData(data.getRes());
            } else {
                // Stop SwipeRefreshLayout refreshing.
                mView.updateData(null);
            }
            boolean autoRenew = SharedPreferencesHelper.getInstance(mView.getContext()).needAutoRenew();
            if (!autoRenew) return;
        }


        // Request data from server.
        if (observer != null) {
            Subscription subscribe = observer
                    .observeOn(Schedulers.io())
                    .doOnNext((BeanWrapper<Bean> blogBeanWrapper) ->
                            // Cache data in IO-thread.
                            aCache.put("DATA" + SECTION_INDEX, gson.toJson(blogBeanWrapper)))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new LSubscriber<BeanWrapper<Bean>>() {

                        @Override
                        protected void onError(int httpStatusCode, MobError error) {
                            mView.showMsg(error.getMsg());
                        }

                        @Override
                        public void onNext(BeanWrapper<Bean> data) {
                            mView.updateData(data.getRes());
                        }
                    });
            mSubscriptions.add(subscribe);
        } else {
            // setRefreshing(false)
            mView.updateData(null);
        }
    }
}
