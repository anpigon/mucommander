/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2007 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.action;

import com.mucommander.ui.main.MainFrame;

import java.util.Hashtable;

/**
 * This action changes the current folder of the currently active {@link com.mucommander.ui.main.FolderPanel} to
 * <code>bookmark://</code> which is the root of bookmark filesystem, allowing to explore all the bookmarks the user has.
 *
 * @author Nicolas Rinaudo
 */
public class ExploreBookmarksAction extends MuAction {

    public ExploreBookmarksAction(MainFrame mainFrame, Hashtable properties) {
        super(mainFrame, properties);
    }

    public void performAction() {
        mainFrame.getActiveTable().getFolderPanel().tryChangeCurrentFolder("bookmark://");
    }
}