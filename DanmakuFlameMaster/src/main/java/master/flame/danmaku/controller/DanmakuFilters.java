
package master.flame.danmaku.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.GlobalFlagValues;
import master.flame.danmaku.danmaku.model.IDanmakuIterator;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.Danmakus;

public class DanmakuFilters {

    public static final int FILTER_TYPE_TYPE = 0x00000001;
    public static final int FILYER_TYPE_QUANTITY = 0x0000002;
    public static final int FILTER_TYPE_ELAPSED_TIME = 0x00000004;
    public static final int FILTER_TYPE_TEXTCOLOR = 0x00000008;
    public static final int FILTER_TYPE_USER_ID = 0x00000020;
    public static final int FILTER_TYPE_USER_HASH = 0x00000040;
    public static final int FILTER_TYPE_USER_GUEST = 0x00000080;
    public static final int FILTER_TYPE_DUPLICATE_MERGE = 0x00000100;


    public static interface IDanmakuFilter<T> {
        /*
         * 是否过滤
         */
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask);

        public void setData(T data);

        public void reset();

        public void clear();

    }

    public static abstract class BaseDanmakuFilter<T> implements IDanmakuFilter<T> {

        @Override
        public void clear() {

        }

    }

    /**
     * 根据弹幕类型过滤
     * 
     * @author ch
     */
    public static class TypeDanmakuFilter extends BaseDanmakuFilter<List<Integer>> {

        final List<Integer> mFilterTypes = Collections.synchronizedList(new ArrayList<Integer>());

        public void enableType(Integer type) {
            if (!mFilterTypes.contains(type))
                mFilterTypes.add(type);
        }

        public void disableType(Integer type) {
            if (mFilterTypes.contains(type))
                mFilterTypes.remove(type);
        }

        @Override
        public void filter(BaseDanmaku danmaku, int orderInScreen, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = danmaku != null && mFilterTypes.contains(danmaku.getType());
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_TYPE;
            }
        }

        @Override
        public void setData(List<Integer> data) {
            reset();
            if (data != null) {
                for (Integer i : data) {
                    enableType(i);
                }
            }
        }

        @Override
        public void reset() {
            mFilterTypes.clear();
        }

    }

    /**
     * 根据同屏数量过滤弹幕
     * 
     * @author ch
     */
    public static class QuantityDanmakuFilter extends BaseDanmakuFilter<Integer> {

        protected int mMaximumSize = -1;

        protected BaseDanmaku mLastSkipped = null;

        private boolean needFilter(BaseDanmaku danmaku, int orderInScreen,
                                 int totalSizeInScreen, DanmakuTimer timer, boolean fromCachingTask) {

            if (mMaximumSize <= 0 || danmaku.getType() != BaseDanmaku.TYPE_SCROLL_RL) {
                return false;
            }

            if (totalSizeInScreen < mMaximumSize || danmaku.isShown()
                    || (mLastSkipped != null && (danmaku.time - mLastSkipped.time > 500))) {
                mLastSkipped = danmaku;
                return false;
            }

            if (orderInScreen > mMaximumSize && !danmaku.isTimeOut()) {
                return true;
            }
            mLastSkipped = danmaku;
            return false;
        }

        @Override
        public synchronized void filter(BaseDanmaku danmaku, int orderInScreen,
                int totalsizeInScreen, DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = needFilter(danmaku, orderInScreen, totalsizeInScreen, timer, fromCachingTask);
            if (filtered) {
                danmaku.mFilterParam |= FILYER_TYPE_QUANTITY;
            }
        }

        @Override
        public void setData(Integer data) {
            reset();
            if (data == null)
                return;
            if (data != mMaximumSize) {
                mMaximumSize = data;
            }
        }

        @Override
        public synchronized void reset() {
            mLastSkipped = null;
        }

        @Override
        public void clear() {
            reset();
        }
    }

    /**
     * 根据绘制耗时过滤弹幕
     * 
     * @author ch
     */
    public static class ElapsedTimeFilter extends BaseDanmakuFilter<Object> {

        long mMaxTime = 20; // 绘制超过20ms就跳过 ，默认保持接近50fps

        private synchronized boolean needFilter(BaseDanmaku danmaku, int orderInScreen,
                                   int totalsizeInScreen, DanmakuTimer timer, boolean fromCachingTask) {
            if (timer == null || !danmaku.isOutside()) {
                return false;
            }

            long elapsedTime = System.currentTimeMillis() - timer.currMillisecond;
            if (elapsedTime >= mMaxTime) {
                return true;
            }
            return false;
        }

        @Override
        public void filter(BaseDanmaku danmaku, int orderInScreen,
                int totalsizeInScreen, DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = needFilter(danmaku, orderInScreen, totalsizeInScreen, timer, fromCachingTask);
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_ELAPSED_TIME;
            }
        }

        @Override
        public void setData(Object data) {
            reset();
        }

        @Override
        public synchronized void reset() {

        }

        @Override
        public void clear() {
            reset();
        }

    }

    /**
     * 根据文本颜色白名单过滤
     * 
     * @author ch
     */
    public static class TextColorFilter extends BaseDanmakuFilter<List<Integer>> {

        public List<Integer> mWhiteList = new ArrayList<Integer>();

        private void addToWhiteList(Integer color) {
            if (!mWhiteList.contains(color)) {
                mWhiteList.add(color);
            }
        }

        @Override
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = danmaku != null && !mWhiteList.contains(danmaku.textColor);
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_TEXTCOLOR;
            }
        }

        @Override
        public void setData(List<Integer> data) {
            reset();
            if (data != null) {
                for (Integer i : data) {
                    addToWhiteList(i);
                }
            }
        }

        @Override
        public void reset() {
            mWhiteList.clear();
        }

    }

    /**
     * 根据用户标识黑名单过滤
     * 
     * @author ch
     */
    public static abstract class UserFilter<T> extends BaseDanmakuFilter<List<T>> {

        public List<T> mBlackList = new ArrayList<T>();

        private void addToBlackList(T id) {
            if (!mBlackList.contains(id)) {
                mBlackList.add(id);
            }
        }

        @Override
        public abstract void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask);

        @Override
        public void setData(List<T> data) {
            reset();
            if (data != null) {
                for (T i : data) {
                    addToBlackList(i);
                }
            }
        }

        @Override
        public void reset() {
            mBlackList.clear();
        }

    }

    /**
     * 根据用户Id黑名单过滤
     * 
     * @author ch
     */
    public static class UserIdFilter extends UserFilter<Integer> {

        @Override
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = danmaku != null && mBlackList.contains(danmaku.userId);
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_USER_ID;
            }
        }

    }

    /**
     * 根据用户hash黑名单过滤
     * 
     * @author ch
     */
    public static class UserHashFilter extends UserFilter<String> {

        @Override
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = danmaku != null && mBlackList.contains(danmaku.userHash);
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_USER_HASH;
            }
        }

    }

    /**
     * 屏蔽游客弹幕
     * 
     * @author ch
     */
    public static class GuestFilter extends BaseDanmakuFilter<Boolean> {

        private Boolean mBlock = false;

        @Override
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = mBlock && danmaku.isGuest;
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_USER_GUEST;
            }
        }

        @Override
        public void setData(Boolean data) {
            mBlock = data;
        }

        @Override
        public void reset() {
            mBlock = false;
        }

    }

    public static class DuplicateMergingFilter extends BaseDanmakuFilter<Void> {

        protected final IDanmakus blockedDanmakus = new Danmakus(Danmakus.ST_BY_LIST);
        protected final LinkedHashMap<String, BaseDanmaku> currentDanmakus = new LinkedHashMap<String, BaseDanmaku>();
        private final IDanmakus passedDanmakus = new Danmakus(Danmakus.ST_BY_LIST);

        private final void removeTimeoutDanmakus(final IDanmakus danmakus, long limitTime) {
            IDanmakuIterator it = danmakus.iterator();
            long startTime = System.currentTimeMillis();
            while (it.hasNext()) {
                try {
                    BaseDanmaku item = it.next();
                    if (item.isTimeOut()) {
                        it.remove();
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
                if (System.currentTimeMillis() - startTime > limitTime) {
                    break;
                }
            }
        }

        private void removeTimeoutDanmakus(LinkedHashMap<String, BaseDanmaku> danmakus,
                int limitTime) {
            Iterator<Entry<String, BaseDanmaku>> it = danmakus.entrySet().iterator();
            long startTime = System.currentTimeMillis();
            while (it.hasNext()) {
                try {
                    Entry<String, BaseDanmaku> entry = it.next();
                    BaseDanmaku item = entry.getValue();
                    if (item.isTimeOut()) {
                        it.remove();
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
                if (System.currentTimeMillis() - startTime > limitTime) {
                    break;
                }
            }
        }

        public synchronized boolean needFilter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                DanmakuTimer timer, boolean fromCachingTask) {
            removeTimeoutDanmakus(blockedDanmakus, 2);
            removeTimeoutDanmakus(passedDanmakus, 2);
            removeTimeoutDanmakus(currentDanmakus, 3);
            if (blockedDanmakus.contains(danmaku) && !danmaku.isOutside()) {
                return true;
            }
            if (passedDanmakus.contains(danmaku)) {
                return false;
            }
            if (currentDanmakus.containsKey(danmaku.text)) {
                currentDanmakus.put(String.valueOf(danmaku.text), danmaku);
                blockedDanmakus.removeItem(danmaku);
                blockedDanmakus.addItem(danmaku);
                return true;
            } else {
                currentDanmakus.put(String.valueOf(danmaku.text), danmaku);
                passedDanmakus.addItem(danmaku);
                return false;
            }

        }

        @Override
        public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
                           DanmakuTimer timer, boolean fromCachingTask) {
            boolean filtered = needFilter(danmaku, index, totalsizeInScreen, timer, fromCachingTask);
            if (filtered) {
                danmaku.mFilterParam |= FILTER_TYPE_DUPLICATE_MERGE;
            }
        }

        @Override
        public void setData(Void data) {

        }

        @Override
        public synchronized void reset() {
            passedDanmakus.clear();
            blockedDanmakus.clear();
            currentDanmakus.clear();
        }

        @Override
        public void clear() {
            reset();
        }

    }

    public final static String TAG_TYPE_DANMAKU_FILTER = "1010_Filter";

    public final static String TAG_QUANTITY_DANMAKU_FILTER = "1011_Filter";

    public final static String TAG_ELAPSED_TIME_FILTER = "1012_Filter";

    public final static String TAG_TEXT_COLOR_DANMAKU_FILTER = "1013_Filter";

    public final static String TAG_USER_ID_FILTER = "1014_Filter";

    public final static String TAG_USER_HASH_FILTER = "1015_Filter";

    public static final String TAG_GUEST_FILTER = "1016_Filter";

    public static final String TAG_DUPLICATE_FILTER = "1017_Filter";

    private static DanmakuFilters instance = null;

    public final Exception filterException = new Exception("not suuport this filter tag");

    public void filter(BaseDanmaku danmaku, int index, int totalsizeInScreen,
            DanmakuTimer timer, boolean fromCachingTask) {
        for (IDanmakuFilter<?> f : mFilterArray) {
            if (f != null) {
                f.filter(danmaku, index, totalsizeInScreen, timer, fromCachingTask);
                danmaku.filterResetFlag = GlobalFlagValues.FILTER_RESET_FLAG;
            }
        }
    }

    private final static Map<String, IDanmakuFilter<?>> filters = Collections
            .synchronizedSortedMap(new TreeMap<String, IDanmakuFilter<?>>());

    public IDanmakuFilter<?> get(String tag) {
        IDanmakuFilter<?> f = filters.get(tag);
        if (f == null) {
            f = registerFilter(tag);
        }
        return f;
    }

    IDanmakuFilter<?>[] mFilterArray = new IDanmakuFilter[0];

    public IDanmakuFilter<?> registerFilter(String tag) {
        if (tag == null) {
            throwFilterException();
            return null;
        }
        IDanmakuFilter<?> filter = filters.get(tag);
        if (filter == null) {
            if (TAG_TYPE_DANMAKU_FILTER.equals(tag)) {
                filter = new TypeDanmakuFilter();
            } else if (TAG_QUANTITY_DANMAKU_FILTER.equals(tag)) {
                filter = new QuantityDanmakuFilter();
            } else if (TAG_ELAPSED_TIME_FILTER.equals(tag)) {
                filter = new ElapsedTimeFilter();
            } else if (TAG_TEXT_COLOR_DANMAKU_FILTER.equals(tag)) {
                filter = new TextColorFilter();
            } else if (TAG_USER_ID_FILTER.equals(tag)) {
                filter = new UserIdFilter();
            } else if (TAG_USER_HASH_FILTER.equals(tag)) {
                filter = new UserHashFilter();
            } else if (TAG_GUEST_FILTER.equals(tag)) {
                filter = new GuestFilter();
            } else if (TAG_DUPLICATE_FILTER.equals(tag)) {
                filter = new DuplicateMergingFilter();
            }
            // add more filter
        }
        if (filter == null) {
            throwFilterException();
            return null;
        }
        filter.setData(null);
        filters.put(tag, filter);
        mFilterArray = filters.values().toArray(mFilterArray);
        return filter;
    }

    public void unregisterFilter(String tag) {
        IDanmakuFilter<?> f = filters.remove(tag);
        if (f != null) {
            f.clear();
            f = null;
            mFilterArray = filters.values().toArray(mFilterArray);
        }
    }

    public void clear() {
        for (IDanmakuFilter<?> f : mFilterArray) {
            if (f != null)
                f.clear();
        }
    }

    public void reset() {
        for (IDanmakuFilter<?> f : mFilterArray) {
            if (f != null)
                f.reset();
        }
    }

    public void release() {
        clear();
        filters.clear();
        mFilterArray = new IDanmakuFilter[0];
    }

    private void throwFilterException() {
        try {
            throw filterException;
        } catch (Exception e) {
        }
    }

    public static DanmakuFilters getDefault() {
        if (instance == null) {
            instance = new DanmakuFilters();
        }
        return instance;
    }

}
