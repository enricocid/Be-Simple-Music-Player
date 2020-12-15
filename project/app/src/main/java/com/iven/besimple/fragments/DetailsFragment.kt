package com.iven.besimple.fragments

import android.animation.Animator
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.google.android.material.card.MaterialCardView
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.MusicViewModel
import com.iven.besimple.R
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.databinding.FragmentDetailsBinding
import com.iven.besimple.extensions.*
import com.iven.besimple.helpers.ListsHelper
import com.iven.besimple.helpers.MusicOrgHelper
import com.iven.besimple.helpers.ThemeHelper
import com.iven.besimple.models.Album
import com.iven.besimple.models.Music
import com.iven.besimple.ui.AlbumsViewHolder
import com.iven.besimple.ui.GenericViewHolder
import com.iven.besimple.ui.UIControlInterface


/**
 * A simple [Fragment] subclass.
 * Use the [DetailsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

class DetailsFragment : Fragment(R.layout.fragment_details), SearchView.OnQueryTextListener {

    private lateinit var mDetailsFragmentBinding: FragmentDetailsBinding

    // View model
    private lateinit var mMusicViewModel: MusicViewModel

    private lateinit var mArtistDetailsAnimator: Animator
    private lateinit var mAlbumsRecyclerViewLayoutManager: LinearLayoutManager

    private val mSelectedAlbumsDataSource = dataSourceOf()
    private val mSongsDataSource = dataSourceOf()

    private var mLaunchedBy = BeSimpleConstants.ARTIST_VIEW

    private var mSelectedArtistAlbums: List<Album>? = null
    private var mSongsList: List<Music>? = null

    private var mSelectedArtistOrFolder: String? = null
    private var mSelectedAlbumPosition = -1

    private lateinit var mUIControlInterface: UIControlInterface

    private var mSelectedAlbum: Album? = null

    private lateinit var mSortMenuItem: MenuItem

    private var mSongsSorting = BeSimpleConstants.TRACK_SORTING

    private val sLaunchedByArtistView get() = mLaunchedBy == BeSimpleConstants.ARTIST_VIEW

    override fun onAttach(context: Context) {
        super.onAttach(context)

        arguments?.getString(TAG_ARTIST_FOLDER)?.let { selectedArtistOrFolder ->
            mSelectedArtistOrFolder = selectedArtistOrFolder
        }

        arguments?.getString(TAG_IS_FOLDER)?.let { launchedBy ->
            mLaunchedBy = launchedBy
        }

        if (sLaunchedByArtistView) {
            arguments?.getInt(TAG_SELECTED_ALBUM_POSITION)?.let { selectedAlbumPosition ->
                mSelectedAlbumPosition = selectedAlbumPosition
            }
        }

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mUIControlInterface = activity as UIControlInterface
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    fun onHandleBackPressed(): Animator {
        if (!mArtistDetailsAnimator.isRunning) {
            mArtistDetailsAnimator =
                    mDetailsFragmentBinding.root.createCircularReveal(
                            isErrorFragment = false,
                            show = false
                    )
        }
        return mArtistDetailsAnimator
    }

    // https://stackoverflow.com/a/38241603
    private fun getTitleTextView(toolbar: Toolbar) = try {
        val toolbarClass = Toolbar::class.java
        val titleTextViewField = toolbarClass.getDeclaredField("mTitleTextView")
        titleTextViewField.isAccessible = true
        titleTextViewField.get(toolbar) as TextView
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun getSongSource(): List<Music>? {
        return when (mLaunchedBy) {
            BeSimpleConstants.ARTIST_VIEW -> {
                mMusicViewModel.deviceAlbumsByArtist?.get(mSelectedArtistOrFolder)
                        ?.let { selectedArtistAlbums ->
                            mSelectedArtistAlbums = selectedArtistAlbums
                        }
                mMusicViewModel.deviceSongsByArtist?.get(mSelectedArtistOrFolder)
            }

            BeSimpleConstants.FOLDER_VIEW ->
                mMusicViewModel.deviceMusicByFolder?.get(mSelectedArtistOrFolder)

            else ->
                mMusicViewModel.deviceMusicByAlbum?.get(mSelectedArtistOrFolder)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mDetailsFragmentBinding = FragmentDetailsBinding.bind(view)

        mMusicViewModel =
                ViewModelProvider(requireActivity()).get(MusicViewModel::class.java).apply {
                    deviceMusic.observe(viewLifecycleOwner, { returnedMusic ->
                        if (!returnedMusic.isNullOrEmpty()) {
                            mSongsList = getSongSource()

                            setupToolbar()

                            setupViews(view)
                        }
                    })
                }
    }

    private fun setupToolbar() {
        mDetailsFragmentBinding.detailsToolbar.run {

            if (!sLaunchedByArtistView) {
                overflowIcon = AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_sort)
            }

            title = mSelectedArtistOrFolder

            // Make toolbar's title scrollable
            getTitleTextView(this)?.let { tV ->
                tV.isSelected = true
                tV.setHorizontallyScrolling(true)
                tV.ellipsize = TextUtils.TruncateAt.MARQUEE
                tV.marqueeRepeatLimit = -1
            }

            setupToolbarSpecs()

            setNavigationOnClickListener {
                requireActivity().onBackPressed()
            }

            if (!sLaunchedByArtistView) {
                setupMenu()
            }
        }
    }

    private fun setupToolbarSpecs() {
        mDetailsFragmentBinding.detailsToolbar.run {
            elevation = if (!sLaunchedByArtistView) {
                resources.getDimensionPixelSize(R.dimen.search_bar_elevation).toFloat()
            } else {
                0F
            }

            val params = layoutParams as LinearLayout.LayoutParams
            params.bottomMargin = if (!sLaunchedByArtistView) {
                0
            } else {
                resources.getDimensionPixelSize(R.dimen.player_controls_padding_normal)
            }
        }
    }

    private fun setupViews(view: View) {

        if (sLaunchedByArtistView) {
            mSelectedAlbum = when {
                mSelectedAlbumPosition != -1 -> mSelectedArtistAlbums?.get(
                        mSelectedAlbumPosition
                )
                else -> {
                    mSelectedAlbumPosition = 0
                    mSelectedArtistAlbums?.get(0)
                }
            }

            setupAlbumsContainer()

            mDetailsFragmentBinding.sortButton.run {
                setOnClickListener {
                    mSongsSorting = ListsHelper.getSongsSorting(mSongsSorting)
                    setImageResource(ThemeHelper.resolveSortAlbumSongsIcon(mSongsSorting))
                    setSongsDataSource(
                            ListsHelper.getSortedMusicList(
                                    mSongsSorting,
                                    mSelectedAlbum?.music
                            )
                    )
                }
            }

        } else {

            mDetailsFragmentBinding.albumsRv.handleViewVisibility(false)
            mDetailsFragmentBinding.selectedAlbumContainer.handleViewVisibility(false)

            mDetailsFragmentBinding.detailsToolbar.subtitle = getString(
                    R.string.folder_info,
                    mSongsList?.size
            )

            mSortMenuItem = ListsHelper.getSelectedSorting(mSongsSorting, mDetailsFragmentBinding.detailsToolbar.menu).apply {
                setTitleColor(ContextCompat.getColor(requireActivity(), R.color.blue))
            }

            val searchView =
                    mDetailsFragmentBinding.detailsToolbar.menu.findItem(R.id.action_search).actionView as SearchView
            searchView.run {
                setOnQueryTextListener(this@DetailsFragment)
                setOnQueryTextFocusChangeListener { _, hasFocus ->
                    mDetailsFragmentBinding.detailsToolbar.menu.setGroupVisible(
                            R.id.sorting,
                            !hasFocus
                    )
                }
            }
        }

        setSongsDataSource(
                if (sLaunchedByArtistView) {
                    mSelectedAlbum?.music
                } else {
                    mSongsList
                }
        )

        mDetailsFragmentBinding.songsRv.run {

            // setup{} is an extension method on RecyclerView
            setup {
                // item is a `val` in `this` here
                withDataSource(mSongsDataSource)

                withItem<Music, GenericViewHolder>(R.layout.generic_item) {
                    onBind(::GenericViewHolder) { _, item ->

                        val displayedTitle =
                                if (beSimplePreferences.songsVisualization != BeSimpleConstants.TITLE) {
                                    item.displayName
                                } else {
                                    getString(
                                            R.string.track_song,
                                            item.track.toFormattedTrack(),
                                            item.title
                                    ).parseAsHtml()
                                }

                        // GenericViewHolder is `this` here
                        title.text = displayedTitle
                        subtitle.text = item.duration.toFormattedDuration(
                                isAlbum = false,
                                isSeekBar = false
                        )
                    }

                    onClick {

                        val selectedPlaylist =
                                if (sLaunchedByArtistView) {
                                    val playlist = MusicOrgHelper.getAlbumSongs(
                                            item.artist,
                                            item.album,
                                            mMusicViewModel.deviceAlbumsByArtist
                                    )
                                    playlist
                                } else {
                                    mSongsList
                                }

                        mUIControlInterface.onSongSelected(
                                item,
                                selectedPlaylist,
                                mLaunchedBy
                        )
                    }
                }
            }
        }

        view.afterMeasured {
            mArtistDetailsAnimator =
                    mDetailsFragmentBinding.root.createCircularReveal(
                            isErrorFragment = false,
                            show = true
                    )
        }
    }

    private fun setAlbumsDataSource(albumsList: List<Album>?) {
        albumsList?.let { albums ->
            mSelectedAlbumsDataSource.set(albums)
        }
    }

    private fun setSongsDataSource(musicList: List<Music>?) {
        if (sLaunchedByArtistView) {
            mDetailsFragmentBinding.sortButton.run {
                isEnabled = mSelectedAlbum?.music?.size!! >= 2
                ThemeHelper.updateIconTint(
                        this,
                        if (isEnabled) {
                            ContextCompat.getColor(requireActivity(), R.color.widgetsColor)
                        } else {
                            ThemeHelper.resolveColorAttr(
                                    requireActivity(),
                                    android.R.attr.colorButtonNormal
                            )
                        }
                )
            }
        }

        musicList?.let { music ->
            mSongsDataSource.set(music)
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        setSongsDataSource(
                ListsHelper.processQueryForMusic(newText, mSongsList)
                        ?: mSongsList
        )
        return false
    }

    override fun onQueryTextSubmit(query: String?) = false

    private fun setupMenu() {

        mDetailsFragmentBinding.detailsToolbar.run {

            inflateMenu(R.menu.menu_album_details)

            menu.setGroupEnabled(R.id.sorting, mSongsList?.size!! >= 2)

            setOnMenuItemClickListener {

                if (it.itemId != R.id.action_search && !sLaunchedByArtistView) {
                    mSortMenuItem.setTitleColor(
                            ThemeHelper.resolveColorAttr(
                                    requireActivity(),
                                    android.R.attr.textColorPrimary
                            )
                    )

                    mSortMenuItem = ListsHelper.getSelectedSorting(it.order, menu).apply {
                        setTitleColor(ContextCompat.getColor(requireActivity(), R.color.blue))
                    }
                }

                when (it.itemId) {
                    R.id.default_sorting -> applySortingToMusic(BeSimpleConstants.DEFAULT_SORTING)
                    R.id.descending_sorting -> applySortingToMusic(BeSimpleConstants.DESCENDING_SORTING)
                    R.id.ascending_sorting -> applySortingToMusic(BeSimpleConstants.ASCENDING_SORTING)
                    R.id.track_sorting -> applySortingToMusic(BeSimpleConstants.TRACK_SORTING)
                    R.id.track_sorting_inv -> applySortingToMusic(BeSimpleConstants.TRACK_SORTING_INVERTED)
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun applySortingToMusic(order: Int) {
        val selectedList = if (sLaunchedByArtistView) {
            mMusicViewModel.deviceMusicByAlbum?.get(mSelectedArtistOrFolder)
        } else {
            mMusicViewModel.deviceMusicByFolder?.get(mSelectedArtistOrFolder)
        }
        mSongsList = ListsHelper.getSortedMusicList(
                order,
                selectedList?.toMutableList()
        )
        setSongsDataSource(mSongsList)
    }

    private fun setupAlbumsContainer() {

        mDetailsFragmentBinding.selectedAlbumContainer.setOnClickListener {
            mAlbumsRecyclerViewLayoutManager.scrollToPositionWithOffset(
                    mSelectedAlbumPosition,
                    0
            )
        }

        mDetailsFragmentBinding.selectedAlbum.isSelected = true

        updateSelectedAlbumTitle()

        setAlbumsDataSource(mSelectedArtistAlbums)

        mDetailsFragmentBinding.detailsToolbar.subtitle = getString(
                R.string.artist_info,
                mSelectedArtistAlbums?.size,
                mSongsList?.size
        )

        mDetailsFragmentBinding.albumsRv.run {

            setHasFixedSize(true)
            setItemViewCacheSize(25)
            setRecycledViewPool(RecyclerView.RecycledViewPool())

            setup {

                withDataSource(mSelectedAlbumsDataSource)

                mAlbumsRecyclerViewLayoutManager = layoutManager as LinearLayoutManager

                withItem<Album, AlbumsViewHolder>(R.layout.album_item) {

                    onBind(::AlbumsViewHolder) { _, item ->
                        // AlbumsViewHolder is `this` here

                        val cardView = itemView as MaterialCardView

                        album.text = item.title

                        year.text = item.year
                        totalDuration.text = item.totalDuration.toFormattedDuration(
                                isAlbum = true,
                                isSeekBar = false
                        )

                        cardView.strokeWidth = if (mSelectedAlbum?.title == item.title) {
                            resources.getDimensionPixelSize(R.dimen.album_stroke)
                        } else {
                            0
                        }
                    }

                    onClick { index ->

                        if (index != mSelectedAlbumPosition) {

                            adapter?.let { albumsAdapter ->

                                albumsAdapter.run {
                                    notifyItemChanged(
                                            mSelectedAlbumPosition
                                    )
                                    notifyItemChanged(index)
                                }

                                mSelectedAlbum = item
                                mSelectedAlbumPosition = index
                                updateSelectedAlbumTitle()
                                swapAlbum(item.music)
                            }
                        } else {
                            mUIControlInterface.onSongSelected(
                                    item.music?.get(0),
                                    item.music,
                                    mLaunchedBy
                            )
                        }
                    }
                }
            }
            if (mSelectedAlbumPosition != -1 || mSelectedAlbumPosition != 0) {
                mAlbumsRecyclerViewLayoutManager.scrollToPositionWithOffset(
                        mSelectedAlbumPosition,
                        0
                )
            }
        }
    }

    fun hasToUpdate(selectedArtistOrFolder: String?) =
            selectedArtistOrFolder != mSelectedArtistOrFolder

    fun tryToSnapToAlbumPosition(snapPosition: Int) {
        if (sLaunchedByArtistView && snapPosition != -1) {
            mDetailsFragmentBinding.albumsRv.smoothSnapToPosition(
                    snapPosition
            )
        }
    }

    private fun updateSelectedAlbumTitle() {
        mDetailsFragmentBinding.selectedAlbum.text = mSelectedAlbum?.title
        mDetailsFragmentBinding.albumYearDuration.text = getString(
                R.string.year_and_duration,
                mSelectedAlbum?.totalDuration?.toFormattedDuration(isAlbum = true, isSeekBar = false),
                mSelectedAlbum?.year
        )
    }

    private fun swapAlbum(songs: MutableList<Music>?) {
        mSongsSorting = BeSimpleConstants.TRACK_SORTING
        mDetailsFragmentBinding.sortButton.setImageResource(
                ThemeHelper.resolveSortAlbumSongsIcon(
                        mSongsSorting
                )
        )
        setSongsDataSource(songs)
        mDetailsFragmentBinding.songsRv.scrollToPosition(0)
    }

    companion object {

        private const val TAG_ARTIST_FOLDER = "SELECTED_ARTIST_FOLDER"
        private const val TAG_IS_FOLDER = "IS_FOLDER"
        private const val TAG_SELECTED_ALBUM_POSITION = "SELECTED_ALBUM_POSITION"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment DetailsFragment.
         */
        @JvmStatic
        fun newInstance(
                selectedArtistOrFolder: String?,
                launchedBy: String,
                playedAlbumPosition: Int
        ) =
                DetailsFragment().apply {
                    arguments = bundleOf(
                            TAG_ARTIST_FOLDER to selectedArtistOrFolder,
                            TAG_IS_FOLDER to launchedBy,
                            TAG_SELECTED_ALBUM_POSITION to playedAlbumPosition
                    )
                }
    }
}
