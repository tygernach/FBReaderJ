/*
 * Copyright (C) 2010-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.library;

import android.app.SearchManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.view.*;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ListView;

import org.geometerplus.zlibrary.core.options.ZLStringOption;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.resources.ZLResource;

import org.geometerplus.zlibrary.ui.android.R;

import org.geometerplus.android.util.UIUtil;

import org.geometerplus.fbreader.library.*;
import org.geometerplus.fbreader.tree.FBTree;

import org.geometerplus.android.fbreader.SQLiteBooksDatabase;
import org.geometerplus.android.fbreader.FBReader;
import org.geometerplus.android.fbreader.BookInfoActivity;

import org.geometerplus.android.fbreader.tree.BaseActivity;
import org.geometerplus.android.fbreader.tree.ListAdapter;

public class LibraryActivity extends BaseActivity implements MenuItem.OnMenuItemClickListener, View.OnCreateContextMenuListener {
	public static final String SELECTED_BOOK_PATH_KEY = "SelectedBookPath";

	static Library LibraryInstance;

	private BooksDatabase myDatabase;

	private Book mySelectedBook;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		myDatabase = SQLiteBooksDatabase.Instance();
		if (myDatabase == null) {
			myDatabase = new SQLiteBooksDatabase(this, "LIBRARY");
		}
		if (LibraryInstance == null) {
			LibraryInstance = new Library();
			startService(new Intent(getApplicationContext(), InitializationService.class));
		}

		final String selectedBookPath = getIntent().getStringExtra(SELECTED_BOOK_PATH_KEY);
		mySelectedBook = null;
		if (selectedBookPath != null) {
			final ZLFile file = ZLFile.createFileByPath(selectedBookPath);
			if (file != null) {
				mySelectedBook = Book.getByFile(file);
			}
		}

		final ListAdapter adapter = new LibraryListAdapter(this);
		init(getIntent());

		getListView().setTextFilterEnabled(true);

		getListView().setOnCreateContextMenuListener(this);
	}

	@Override
	protected FBTree getTreeByKey(FBTree.Key key) {
		return key != null ? LibraryInstance.getLibraryTree(key) : LibraryInstance.getRootTree();
	}

	@Override
	protected void onDestroy() {
		LibraryInstance = null;
		super.onDestroy();
	}

	@Override
	public boolean isTreeSelected(FBTree tree) {
		final LibraryTree lTree = (LibraryTree)tree;
		return lTree.isSelectable() && lTree.containsBook(mySelectedBook);
	}

	@Override
	protected void onListItemClick(ListView listView, View view, int position, long rowId) {
		final LibraryTree tree = (LibraryTree)getListAdapter().getItem(position);
		final Book book = tree.getBook();
		if (book != null) {
			showBookInfo(book);
		} else {
			openTree(tree);
		}
	}

	//
	// show BookInfoActivity
	//
	private static final int BOOK_INFO_REQUEST = 1;

	protected void showBookInfo(Book book) {
		startActivityForResult(
			new Intent(getApplicationContext(), BookInfoActivity.class)
				.putExtra(BookInfoActivity.CURRENT_BOOK_PATH_KEY, book.File.getPath()),
			BOOK_INFO_REQUEST
		);
	}

	@Override
	protected void onActivityResult(int requestCode, int returnCode, Intent intent) {
		if (requestCode == BOOK_INFO_REQUEST) {
			getListView().invalidateViews();
		}
	} 

	//
	// Search
	//
	static final ZLStringOption BookSearchPatternOption =
		new ZLStringOption("BookSearch", "Pattern", "");

	@Override
	protected void onNewIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			if (runSearch(intent)) {
				openSearchResults();
			} else {
				UIUtil.showErrorMessage(this, "bookNotFound");
			}
		} else {
			super.onNewIntent(intent);
		}
	}

	private void openSearchResults() {
		FBTree tree = getCurrentTree();
		while (tree.Parent != null) {
			tree = tree.Parent;
		}
		tree = tree.getSubTree(Library.ROOT_SEARCH_RESULTS);
		if (tree != null) {
			openTree(tree);
		}
	}

	@Override
	public boolean onSearchRequested() {
		startSearch(BookSearchPatternOption.getValue(), true, null, false);
		return true;
	}

	private boolean runSearch(Intent intent) {
		final String pattern = intent.getStringExtra(SearchManager.QUERY);
		if (pattern == null || pattern.length() == 0) {
			return false;
		}
		BookSearchPatternOption.setValue(pattern);
		return LibraryInstance.searchBooks(pattern) != null;
	}

	//
	// Context menu
	//
	private static final int OPEN_BOOK_ITEM_ID = 0;
	private static final int SHOW_BOOK_INFO_ITEM_ID = 1;
	private static final int ADD_TO_FAVORITES_ITEM_ID = 2;
	private static final int REMOVE_FROM_FAVORITES_ITEM_ID = 3;
	private static final int DELETE_BOOK_ITEM_ID = 4;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
		final int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
		final Book book = ((LibraryTree)getListAdapter().getItem(position)).getBook();
		if (book != null) {
			createBookContextMenu(menu, book); 
		}
	}

	private void createBookContextMenu(ContextMenu menu, Book book) {
		final ZLResource resource = Library.resource();
		menu.setHeaderTitle(book.getTitle());
		menu.add(0, OPEN_BOOK_ITEM_ID, 0, resource.getResource("openBook").getValue());
		menu.add(0, SHOW_BOOK_INFO_ITEM_ID, 0, resource.getResource("showBookInfo").getValue());
		if (LibraryInstance.isBookInFavorites(book)) {
			menu.add(0, REMOVE_FROM_FAVORITES_ITEM_ID, 0, resource.getResource("removeFromFavorites").getValue());
		} else {
			menu.add(0, ADD_TO_FAVORITES_ITEM_ID, 0, resource.getResource("addToFavorites").getValue());
		}
		if ((LibraryInstance.getRemoveBookMode(book) & Library.REMOVE_FROM_DISK) != 0) {
			menu.add(0, DELETE_BOOK_ITEM_ID, 0, resource.getResource("deleteBook").getValue());
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;
		final Book book = ((LibraryTree)getListAdapter().getItem(position)).getBook();
		if (book != null) {
			return onContextItemSelected(item.getItemId(), book);
		}
		return super.onContextItemSelected(item);
	}

	private boolean onContextItemSelected(int itemId, Book book) {
		switch (itemId) {
			case OPEN_BOOK_ITEM_ID:
				openBook(book);
				return true;
			case SHOW_BOOK_INFO_ITEM_ID:
				showBookInfo(book);
				return true;
			case ADD_TO_FAVORITES_ITEM_ID:
				LibraryInstance.addBookToFavorites(book);
				return true;
			case REMOVE_FROM_FAVORITES_ITEM_ID:
				LibraryInstance.removeBookFromFavorites(book);
				getListView().invalidateViews();
				return true;
			case DELETE_BOOK_ITEM_ID:
				tryToDeleteBook(book);
				return true;
		}
		return false;
	}

	private void openBook(Book book) {
		startActivity(
			new Intent(getApplicationContext(), FBReader.class)
				.setAction(Intent.ACTION_VIEW)
				.putExtra(FBReader.BOOK_PATH_KEY, book.File.getPath())
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
		);
	}

	//
	// Options menu
	//

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		addMenuItem(menu, 1, "localSearch", R.drawable.ic_menu_search);
		return true;
	}

	private MenuItem addMenuItem(Menu menu, int index, String resourceKey, int iconId) {
		final String label = Library.resource().getResource("menu").getResource(resourceKey).getValue();
		final MenuItem item = menu.add(0, index, Menu.NONE, label);
		item.setOnMenuItemClickListener(this);
		item.setIcon(iconId);
		return item;
	}

	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
			case 1:
				return onSearchRequested();
			default:
				return true;
		}
	}

	//
	// Book deletion
	//
	private class BookDeleter implements DialogInterface.OnClickListener {
		private final Book myBook;
		private final int myMode;

		BookDeleter(Book book, int removeMode) {
			myBook = book;
			myMode = removeMode;
		}

		public void onClick(DialogInterface dialog, int which) {
			deleteBook(myBook, myMode);
		}
	}

	private void tryToDeleteBook(Book book) {
		final ZLResource dialogResource = ZLResource.resource("dialog");
		final ZLResource buttonResource = dialogResource.getResource("button");
		final ZLResource boxResource = dialogResource.getResource("deleteBookBox");
		new AlertDialog.Builder(this)
			.setTitle(book.getTitle())
			.setMessage(boxResource.getResource("message").getValue())
			.setIcon(0)
			.setPositiveButton(buttonResource.getResource("yes").getValue(), new BookDeleter(book, Library.REMOVE_FROM_DISK))
			.setNegativeButton(buttonResource.getResource("no").getValue(), null)
			.create().show();
	}

	private void deleteBook(Book book, int mode) {
		LibraryInstance.removeBook(book, mode);

		if (getCurrentTree() instanceof FileTree) {
			getListAdapter().remove(new FileTree((FileTree)getCurrentTree(), book.File));
		} else {
			getListAdapter().replaceAll(getCurrentTree().subTrees());
		}
		getListView().invalidateViews();
	}
}
