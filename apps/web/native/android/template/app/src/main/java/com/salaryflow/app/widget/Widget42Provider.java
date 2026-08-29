package com.salaryflow.app.widget;

/** 小组件 · 4x2（对应 @xml/widget_4x2 元数据）。 */
public class Widget42Provider extends EarningsWidgetProvider {
    @Override
    protected int defaultLayout() {
        return WidgetRenderer.LAYOUT_4X2;
    }
}
