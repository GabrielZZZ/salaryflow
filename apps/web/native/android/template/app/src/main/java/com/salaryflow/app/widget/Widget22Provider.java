package com.salaryflow.app.widget;

/** 小组件 · 2x2（对应 @xml/widget_2x2 元数据）。 */
public class Widget22Provider extends EarningsWidgetProvider {
    @Override
    protected int defaultLayout() {
        return WidgetRenderer.LAYOUT_2X2;
    }
}
