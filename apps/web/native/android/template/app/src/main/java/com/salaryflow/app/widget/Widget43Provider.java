package com.salaryflow.app.widget;

/** 小组件 · 4x3（对应 @xml/widget_4x3 元数据）。 */
public class Widget43Provider extends EarningsWidgetProvider {
    @Override
    protected int defaultLayout() {
        return WidgetRenderer.LAYOUT_4X3;
    }
}
