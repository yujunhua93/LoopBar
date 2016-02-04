package cleveroad.com.lib.widget;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

import cleveroad.com.lib.R;
import cleveroad.com.lib.adapter.SimpleCategoriesAdapter;
import cleveroad.com.lib.model.MockedItemsFactory;
import cleveroad.com.lib.util.AbstractAnimatorListener;

import static cleveroad.com.lib.adapter.SimpleCategoriesAdapter.ICategoryItem;

public class EndlessNavigationView extends FrameLayout implements OnItemClickListener<SimpleCategoriesAdapter.ICategoryItem> {
    public static final int ORIENTATION_VERTICAL = 0;
    public static final int ORIENTATION_HORIZONTAL = 1;
    public static final int SELECTION_GRAVITY_START = 0;
    public static final int SELECTION_GRAVITY_END = 1;
    private static final String TAG = EndlessNavigationView.class.getSimpleName();

    //view settings
    private Animator selectionInAnimator;
    private Animator selectionOutAnimator;
    private int selectionMargin;

    private List<ICategoryItem> items = MockedItemsFactory.getCategoryItemsUniq();
    private int realHidedPosition = 0;
    private FrameLayout flContainerSelected;
    private RecyclerView rvCategories;
    private SimpleCategoriesAdapter.CategoriesHolder categoriesHolder;
    private SimpleCategoriesAdapter categoriesAdapter;

    private LinearLayoutManager linearLayoutManager;
    private SpacesItemDecoration spacesItemDecoration = new SpacesItemDecoration(0);
    private boolean skipNextOnLayout;
    private boolean isIndeterminateInitialized;
    private RecyclerView.OnScrollListener indeterminateOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (linearLayoutManager.findFirstVisibleItemPosition() == 0 || linearLayoutManager.findFirstVisibleItemPosition() == Integer.MAX_VALUE) {
                linearLayoutManager.scrollToPosition(Integer.MAX_VALUE / 2);
            }
        }
    };

    public EndlessNavigationView(Context context) {
        super(context);
        init(context, null);
    }

    public EndlessNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public EndlessNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EndlessNavigationView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void inflate(IOrientationState orientationState) {
        inflate(getContext(), orientationState.getLayoutId(), this);
        flContainerSelected = (FrameLayout) findViewById(R.id.flContainerSelected);
        rvCategories = (RecyclerView) findViewById(R.id.rvCategories);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        //read customization attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EndlessNavigationView);
        int colorListBackground = a.getColor(R.styleable.EndlessNavigationView_listBackground,
                ContextCompat.getColor(getContext(), R.color.default_list_background));
        int colorSelectionView = a.getColor(R.styleable.EndlessNavigationView_selectionBackground,
                ContextCompat.getColor(getContext(), R.color.default_selection_view_background));
        int orientation = a.getInteger(R.styleable.EndlessNavigationView_orientation, ORIENTATION_HORIZONTAL);
        int selectionAnimatorInId = a.getResourceId(R.styleable.EndlessNavigationView_selectionInAnimation, R.animator.scale_restore);
        int selectionAnimatorOutId = a.getResourceId(R.styleable.EndlessNavigationView_selectionOutAnimation, R.animator.scale_small);
        @GravityAttr int selectionGravity = a.getInteger(R.styleable.EndlessNavigationView_selectionGravity, SELECTION_GRAVITY_START);
        selectionMargin = a.getDimensionPixelSize(R.styleable.EndlessNavigationView_selectionMargin, getResources().getDimensionPixelSize(R.dimen.margin_selected_view));
        a.recycle();
        selectionInAnimator = AnimatorInflater.loadAnimator(getContext(), selectionAnimatorInId);
        selectionOutAnimator = AnimatorInflater.loadAnimator(getContext(), selectionAnimatorOutId);

        //current view has two state : horizontal & vertical. State design pattern
        IOrientationState orientationState = getOrientationStateFromParam(orientation, selectionGravity);

        inflate(orientationState);

        //note that flContainerSelected should be in FrameLayout
        FrameLayout.LayoutParams params = (LayoutParams) flContainerSelected.getLayoutParams();
        params.gravity = orientationState.getSelectionGravity();
        orientationState.setSelectionMargin(selectionMargin, params);
        flContainerSelected.setLayoutParams(params);

        linearLayoutManager = orientationState.getLayoutManager(getContext());
        rvCategories.setLayoutManager(linearLayoutManager);

        rvCategories.setBackgroundColor(colorListBackground);
        flContainerSelected.setBackgroundColor(colorSelectionView);

        items.get(0).setVisible(false);

        categoriesAdapter = new SimpleCategoriesAdapter(items, this);
        rvCategories.setAdapter(categoriesAdapter);

        View itemView = SimpleCategoriesAdapter.createView(flContainerSelected);
        categoriesHolder = SimpleCategoriesAdapter.CategoriesHolder.newBuilder(itemView).build();
        //set first item to selectionView
        categoriesHolder.bindItem(items.get(0));
        flContainerSelected.addView(itemView);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        Log.d(TAG, "onLayout");

        if (!skipNextOnLayout) {
            if (rvCategories.getChildCount() > 0) {
                int itemWidth = rvCategories.getChildAt(1).getWidth();
                int itemsWidth = itemWidth * (items.size() - 1);

                //if all items of recyclerView fit on screen
                boolean isFitOnScreen = rvCategories.getWidth() >= itemsWidth;

                if (isFitOnScreen) {
                    rvCategories.removeItemDecoration(spacesItemDecoration);
                    Log.i(TAG, "all items fit on screen");
                    categoriesAdapter.setIndeterminate(false);
                    spacesItemDecoration.setSpace(selectionMargin + categoriesHolder.itemView.getWidth());
                    rvCategories.addItemDecoration(spacesItemDecoration);
                    //changing item decoration will call onLayout again, so this flag needed to avoid indeterminate loop
                    skipNextOnLayout = true;

                    rvCategories.removeOnScrollListener(indeterminateOnScrollListener);
                } else {
                    if (!isIndeterminateInitialized) {
                        //scroll to middle of indeterminate recycler view on initialization and if user somehow scrolled to start or end
                        linearLayoutManager.scrollToPositionWithOffset(Integer.MAX_VALUE / 2, getResources().getDimensionPixelOffset(R.dimen.selected_view_size_plus_margin));
                        rvCategories.addOnScrollListener(indeterminateOnScrollListener);
                        isIndeterminateInitialized = true;
                    }
                }
            }
        } else {
            skipNextOnLayout = false;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void startSelectedViewOutAnimation(final ICategoryItem item) {
        Animator animator = selectionOutAnimator;
        animator.setTarget(categoriesHolder.itemView);
        animator.start();
        animator.addListener(new AbstractAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //replace selected view
                categoriesHolder.bindItem(item);
                startSelectedViewInAnimation();
            }
        });
    }

    private void startSelectedViewInAnimation() {
        Animator animator = selectionInAnimator;
        animator.setTarget(categoriesHolder.itemView);
        animator.start();
    }

    @Override
    public void onItemClicked(SimpleCategoriesAdapter.ICategoryItem item, int position) {
        ICategoryItem oldHidedItem = items.get(realHidedPosition);

        int realPosition = position % items.size();
        int itemToShowAdapterPosition = position - realPosition + realHidedPosition;

        item.setVisible(false);

        startSelectedViewOutAnimation(item);

        categoriesAdapter.notifyItemChanged(position);
        realHidedPosition = realPosition;

        oldHidedItem.setVisible(true);
        categoriesAdapter.notifyItemChanged(itemToShowAdapterPosition);

        Log.i(TAG, "clicked on position =" + position);
    }

    //orientation state factory method
    public IOrientationState getOrientationStateFromParam(int orientation, @GravityAttr int selectionGravityState) {
        return orientation == ORIENTATION_VERTICAL ? new OrientationStateVertical(selectionGravityState) : new OrientationStateHorizontal(selectionGravityState);
    }

    @IntDef({SELECTION_GRAVITY_START, SELECTION_GRAVITY_END})
    @interface GravityAttr {
    }

}
