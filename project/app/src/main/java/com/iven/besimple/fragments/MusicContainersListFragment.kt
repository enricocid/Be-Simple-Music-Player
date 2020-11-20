package com.iven.besimple.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.recyclical.datasource.emptyDataSource
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.MusicViewModel
import com.iven.besimple.R
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.databinding.FragmentMusicContainerListBinding
import com.iven.besimple.extensions.afterMeasured
import com.iven.besimple.extensions.decodeColor
import com.iven.besimple.extensions.handleViewVisibility
import com.iven.besimple.extensions.setTitleColor
import com.iven.besimple.helpers.ListsHelper
import com.iven.besimple.helpers.ThemeHelper
import com.iven.besimple.ui.GenericViewHolder
import com.iven.besimple.ui.UIControlInterface
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView

/**
 * A simple [Fragment] subclass.
 * Use the [MusicContainersListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MusicContainersListFragment : Fragment(R.layout.fragment_music_container_list),
        SearchView.OnQueryTextListener {

    private lateinit var mMusicContainerListBinding: FragmentMusicContainerListBinding

    // View model
    private lateinit var mMusicViewModel: MusicViewModel

    private var mLaunchedBy = BeSimpleConstants.ARTIST_VIEW

    private var mList: MutableList<String>? = null

    private val mDataSource = emptyDataSource()

    private lateinit var mUIControlInterface: UIControlInterface

    private lateinit var mSortMenuItem: MenuItem
    private var mSorting = BeSimpleConstants.DESCENDING_SORTING

    private var sIsFastScroller = false
    private val sIsFastScrollerVisible get() = sIsFastScroller && mSorting != BeSimpleConstants.DEFAULT_SORTING
    private var sLandscape = false

    private val mResolvedAccentColor by lazy { R.color.blue.decodeColor(requireActivity()) }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        arguments?.getString(TAG_LAUNCHED_BY)?.let { launchedBy ->
            mLaunchedBy = launchedBy
        }

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mUIControlInterface = activity as UIControlInterface
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mMusicContainerListBinding = FragmentMusicContainerListBinding.bind(view)

        sLandscape = ThemeHelper.isDeviceLand(resources)

        mMusicViewModel = ViewModelProvider(requireActivity()).get(MusicViewModel::class.java)

        mMusicViewModel.deviceMusic.observe(viewLifecycleOwner, { returnedMusic ->
            if (!returnedMusic.isNullOrEmpty()) {
                mSorting = getSortingMethodFromPrefs()

                mList = getSortedItemKeys()

                setListDataSource(mList)

                finishSetup()
            }
        })
    }

    private fun finishSetup() {
        mMusicContainerListBinding.artistsFoldersRv.apply {

            // setup{} is an extension method on RecyclerView
            setup {

                // item is a `val` in `this` here
                withDataSource(mDataSource)

                withItem<String, GenericViewHolder>(R.layout.generic_item) {

                    onBind(::GenericViewHolder) { _, item ->
                        // GenericViewHolder is `this` here
                        title.text = item
                        subtitle.text = getItemsSubtitle(item)
                    }

                    onClick {
                        if (::mUIControlInterface.isInitialized) {
                            mUIControlInterface.onArtistOrFolderSelected(
                                    item,
                                    mLaunchedBy
                            )
                        }
                    }
                }
            }
        }

        setupIndicatorFastScrollerView()

        mMusicContainerListBinding.searchToolbar.apply {

            inflateMenu(R.menu.menu_search)

            overflowIcon =
                    AppCompatResources.getDrawable(context, R.drawable.ic_sort)

            title = getFragmentTitle()

            setNavigationOnClickListener {
                mUIControlInterface.onCloseActivity()
            }

            menu.apply {

                mSortMenuItem = ListsHelper.getSelectedSorting(mSorting, this).apply {
                    setTitleColor(mResolvedAccentColor)
                }

                val searchView = findItem(R.id.action_search).actionView as SearchView

                searchView.apply {
                    setOnQueryTextListener(this@MusicContainersListFragment)
                    setOnQueryTextFocusChangeListener { _, hasFocus ->
                        if (sIsFastScrollerVisible) {
                            mMusicContainerListBinding.fastscroller.handleViewVisibility(!hasFocus)
                            mMusicContainerListBinding.fastscrollerThumb.handleViewVisibility(
                                    !hasFocus
                            )
                            setupArtistsRecyclerViewPadding(hasFocus)
                        }
                        menu.setGroupVisible(R.id.sorting, !hasFocus)
                    }
                }
                setMenuOnItemClickListener(requireActivity(), this)
            }
        }
    }

    private fun getItemsSubtitle(item: String): String? {
        return when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW ->
                getArtistSubtitle(item)
            BeSimpleConstants.FOLDER_VIEW ->
                getString(
                        R.string.folder_info,
                        mMusicViewModel.deviceMusicByFolder?.getValue(item)?.size
                )
            else ->
                mMusicViewModel.deviceMusicByAlbum?.get(item)?.first()?.artist
        }
    }

    private fun getSortedItemKeys(): MutableList<String>? {
        return when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW ->
                ListsHelper.getSortedList(
                        mSorting,
                        mMusicViewModel.deviceAlbumsByArtist?.keys?.toMutableList()
                )
            BeSimpleConstants.FOLDER_VIEW ->
                ListsHelper.getSortedList(
                        mSorting,
                        mMusicViewModel.deviceMusicByFolder?.keys?.toMutableList()
                )

            else ->
                ListsHelper.getSortedListWithNull(
                        mSorting,
                        mMusicViewModel.deviceMusicByAlbum?.keys?.toMutableList()
                )
        }
    }

    private fun getSortingMethodFromPrefs(): Int {
        return when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW ->
                beSimplePreferences.artistsSorting
            else ->
                beSimplePreferences.foldersSorting
        }
    }

    private fun getFragmentTitle(): String {
        val stringId = when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW ->
                R.string.artists
            else ->
                R.string.folders
        }
        return getString(stringId)
    }

    private fun setListDataSource(selectedList: List<String>?) {
        if (!selectedList.isNullOrEmpty()) {
            mDataSource.set(selectedList)
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        setListDataSource(ListsHelper.processQueryForStringsLists(newText, mList) ?: mList)
        return false
    }

    override fun onQueryTextSubmit(query: String?) = false

    private fun getArtistSubtitle(item: String) = getString(
            R.string.artist_info,
            mMusicViewModel.deviceAlbumsByArtist?.getValue(item)?.size,
            mMusicViewModel.deviceSongsByArtist?.getValue(item)?.size
    )

    @SuppressLint("DefaultLocale")
    private fun setupIndicatorFastScrollerView() {

        // Set indexes if artists rv is scrollable
        mMusicContainerListBinding.artistsFoldersRv.afterMeasured {

            sIsFastScroller = computeVerticalScrollRange() > height

            if (sIsFastScroller) {

                mMusicContainerListBinding.fastscroller.setupWithRecyclerView(
                        this,
                        { position ->
                            val item = mList?.get(position) // Get your model object
                            // or fetch the section at [position] from your database

                            FastScrollItemIndicator.Text(
                                    item?.substring(
                                            0,
                                            1
                                    )?.toUpperCase()!! // Grab the first letter and capitalize it
                            ) // Return a text tab_indicator
                        }, showIndicator = { _, indicatorPosition, totalIndicators ->
                    // Hide every other indicator
                    if (sLandscape) {
                        indicatorPosition % 2 == 0
                    } else {
                        if (totalIndicators >= 30) {
                            indicatorPosition % 2 == 0
                        } else {
                            true
                        }
                    }
                }
                )

                mMusicContainerListBinding.fastscrollerThumb.setupWithFastScroller(
                        mMusicContainerListBinding.fastscroller
                )

                mMusicContainerListBinding.fastscroller.useDefaultScroller = false
                mMusicContainerListBinding.fastscroller.itemIndicatorSelectedCallbacks += object :
                        FastScrollerView.ItemIndicatorSelectedCallback {
                    override fun onItemIndicatorSelected(
                            indicator: FastScrollItemIndicator,
                            indicatorCenterY: Int,
                            itemPosition: Int
                    ) {
                        val artistsLayoutManager = layoutManager as LinearLayoutManager
                        artistsLayoutManager.scrollToPositionWithOffset(itemPosition, 0)
                    }
                }
            }

            handleIndicatorFastScrollerViewVisibility()

            setupArtistsRecyclerViewPadding(false)
        }
    }

    private fun setupArtistsRecyclerViewPadding(forceNoPadding: Boolean) {
        val rvPaddingEnd =
                if (sIsFastScrollerVisible && !forceNoPadding) {
                    resources.getDimensionPixelSize(R.dimen.fast_scroller_view_dim)
                } else {
                    0
                }
        mMusicContainerListBinding.artistsFoldersRv.setPadding(0, 0, rvPaddingEnd, 0)
    }

    private fun handleIndicatorFastScrollerViewVisibility() {
        mMusicContainerListBinding.fastscroller.handleViewVisibility(sIsFastScrollerVisible)
        mMusicContainerListBinding.fastscrollerThumb.handleViewVisibility(sIsFastScrollerVisible)
    }

    private fun setMenuOnItemClickListener(context: Context, menu: Menu) {
        mMusicContainerListBinding.searchToolbar.setOnMenuItemClickListener {

            if (it.itemId != R.id.action_search) {

                mSorting = it.order

                mList = getSortedItemKeys()

                handleIndicatorFastScrollerViewVisibility()

                setupArtistsRecyclerViewPadding(false)

                setListDataSource(mList)

                mSortMenuItem.setTitleColor(
                        ThemeHelper.resolveColorAttr(
                                context,
                                android.R.attr.textColorPrimary
                        )
                )

                mSortMenuItem = ListsHelper.getSelectedSorting(mSorting, menu).apply {
                    setTitleColor(mResolvedAccentColor)
                }

                saveSortingMethodToPrefs(mSorting)
            }

            return@setOnMenuItemClickListener true
        }
    }

    private fun saveSortingMethodToPrefs(sortingMethod: Int) {
        when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW ->
                beSimplePreferences.artistsSorting = sortingMethod
            else ->
                beSimplePreferences.foldersSorting = sortingMethod
        }
    }

    companion object {

        private const val TAG_LAUNCHED_BY = "SELECTED_FRAGMENT"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment MusicContainersListFragment.
         */
        @JvmStatic
        fun newInstance(launchedBy: String) = MusicContainersListFragment().apply {
            arguments = Bundle().apply {
                putString(TAG_LAUNCHED_BY, launchedBy)
            }
        }
    }
}
