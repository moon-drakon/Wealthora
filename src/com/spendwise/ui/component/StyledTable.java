package com.spendwise.ui.component;

import com.spendwise.ui.theme.AppColors;
import com.spendwise.ui.theme.AppFonts;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.Dimension;
import javax.swing.table.TableModel;

public final class StyledTable extends JTable {

    public StyledTable(TableModel model) {
        super(model);
        setFont(AppFonts.body());
        setRowHeight(34);
        setFillsViewportHeight(true);
        setAutoCreateRowSorter(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setShowVerticalLines(false);
        setGridColor(AppColors.border());
        setSelectionBackground(AppColors.selectionBackground());
        getTableHeader().setFont(AppFonts.button());
        getTableHeader().setPreferredSize(new Dimension(0, 34));
        setIntercellSpacing(new Dimension(0, 1));
    }
}
