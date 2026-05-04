package com.m4r71n.irshark.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class BaseIrSharkWidgetReceiver : GlanceAppWidgetReceiver() {
    final override val glanceAppWidget: GlanceAppWidget = IrSharkWidget()
}

class IrSharkWidgetReceiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget1x2Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget1x4Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget2x1Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget4x1Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget2x2Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget4x2Receiver : BaseIrSharkWidgetReceiver()

class IrSharkWidget3x3Receiver : BaseIrSharkWidgetReceiver()
