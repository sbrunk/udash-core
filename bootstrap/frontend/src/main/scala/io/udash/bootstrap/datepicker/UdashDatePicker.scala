package io.udash.bootstrap.datepicker

import java.{util => ju}

import io.udash._
import io.udash.bootstrap.{BootstrapStyles, Listenable, ListenableEvent, UdashBootstrap, UdashBootstrapComponent}
import io.udash.bootstrap.BootstrapStyles.BootstrapClass
import io.udash.bootstrap.UdashBootstrap.ComponentId
import io.udash.wrappers.jquery._
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** Wrapper for <a href="http://eonasdan.github.io/bootstrap-datetimepicker/">Bootstrap 3 Datepicker</a>. */
class UdashDatePicker private[datepicker](val date: Property[ju.Date],
                                          val options: ReadableProperty[UdashDatePicker.DatePickerOptions],
                                          override val componentId: ComponentId)
  extends UdashBootstrapComponent with Listenable[UdashDatePicker, UdashDatePicker.DatePickerEvent] with StrictLogging {
  import UdashDatePicker._

  import scalatags.JsDom.all._

  private lazy val dateString: Property[String] = date.transform(dateToMomentString, momentStringToDate)
  private lazy val inp = input(id := componentId.id, tpe := "text", BootstrapStyles.Form.formControl).render
  private lazy val jQInput = jQ(inp).asDatePicker()

  /** Shows date picker widget. */
  def show(): Unit =
    jQInput.dpData().show()

  /** Hides date picker widget. */
  def hide(): Unit =
    jQInput.dpData().hide()

  /** Toggle date picker widget visibility. */
  def toggle(): Unit =
    jQInput.dpData().toggle()

  /** Enables date input. */
  def enable(): Unit =
    jQInput.dpData().enable()

  /** Disables date input. */
  def disable(): Unit =
    jQInput.dpData().disable()

  lazy val render: dom.Element = {
    jQInput.datetimepicker(optionsToJsDict(options.get))

    dateString.listen(s => jQInput.dpData().date(s))
    options.listen(opts => jQInput.dpData().options(optionsToJsDict(opts)))

    jQInput.dpData().date(dateString.get)

    jQInput.on("dp.change", (_: dom.Element, ev: JQueryEvent) => {
      val date: MomentFormatWrapper = ev.asInstanceOf[DatePickerChangeJQEvent].date
      val oldDate: MomentFormatWrapper = ev.asInstanceOf[DatePickerChangeJQEvent].oldDate
      dateString.set(Try(date.format(options.get.format)).getOrElse(null))
      fire(UdashDatePicker.DatePickerEvent.Change(this, momentToDate(date), momentToDate(oldDate)))
    })
    jQInput.on("dp.hide", (_: dom.Element, ev: JQueryEvent) => {
      val date: MomentFormatWrapper = ev.asInstanceOf[DatePickerHideJQEvent].date
      fire(UdashDatePicker.DatePickerEvent.Hide(this, momentToDate(date)))
    })
    jQInput.on("dp.show", (_: dom.Element, ev: JQueryEvent) =>
      fire(UdashDatePicker.DatePickerEvent.Show(this))
    )
    jQInput.on("dp.error", (_: dom.Element, ev: JQueryEvent) => {
      val date: MomentFormatWrapper = ev.asInstanceOf[DatePickerErrorJQEvent].date
      fire(UdashDatePicker.DatePickerEvent.Error(this, momentToDate(date)))
    })

    inp
  }

  private def optionsToJsDict(options: UdashDatePicker.DatePickerOptions): js.Dictionary[js.Any] = {
    import scalajs.js.JSConverters._

    if (options.disabledDates.nonEmpty && options.enabledDates.nonEmpty)
      logger.warn("You should not use both `disabledDates` and `enabledDates` option!")

    js.Dictionary[js.Any](
      "format" -> options.format,
      "dayViewHeaderFormat" -> options.dayViewHeaderFormat,
      "extraFormats" -> (if (options.extraFormats.nonEmpty) options.extraFormats.toJSArray else false),
      "stepping" -> options.stepping,
      "useCurrent" -> options.useCurrent,
      "collapse" -> options.collapse,
      "disabledDates" -> (if (options.disabledDates.nonEmpty) options.disabledDates.map(dateToMoment).toJSArray else false),
      "enabledDates" -> (if (options.enabledDates.nonEmpty) options.enabledDates.map(dateToMoment).toJSArray else false),
      "icons" -> iconsOptionToJSDict(options.icons),
      "useStrict" -> options.useStrict,
      "sideBySide" -> options.sideBySide,
      "daysOfWeekDisabled" -> (if (options.daysOfWeekDisabled.nonEmpty) options.daysOfWeekDisabled.map(_.id).toJSArray else false),
      "calendarWeeks" -> options.calendarWeeks,
      "viewMode" -> options.viewMode.id,
      "showTodayButton" -> options.showTodayButton,
      "showClear" -> options.showClear,
      "showClose" -> options.showClose,
      "keepOpen" -> options.keepOpen,
      "inline" -> options.inline,
      "keepInvalid" -> options.keepInvalid,
      "ignoreReadonly" -> options.ignoreReadonly,
      "allowInputToggle" -> options.allowInputToggle,
      "focusOnShow" -> options.focusOnShow,
      "enabledHours" -> (if (options.enabledHours.nonEmpty) options.enabledHours.toJSArray else false),
      "disabledHours" -> (if (options.disabledHours.nonEmpty) options.disabledHours.toJSArray else false),
      "viewDate" -> options.viewDate,
      "tooltips" -> tooltipsOptionToJSDict(options.tooltips),
      "locale" -> (if (options.locale.nonEmpty) options.locale.get else false),
      "widgetParent" -> (if (options.widgetParent.nonEmpty) options.widgetParent.get else null),
      "minDate" -> (if (options.minDate.nonEmpty) dateToMoment(options.minDate.get) else false),
      "maxDate" -> (if (options.maxDate.nonEmpty) dateToMoment(options.maxDate.get) else false),
      "defaultDate" -> (if (options.defaultDate.nonEmpty) dateToMoment(options.defaultDate.get) else false),
      "toolbarPlacement" -> (if (options.toolbarPlacement.nonEmpty) options.toolbarPlacement.get.name else UdashDatePicker.Placement.DefaultPlacement.name),
      "widgetPositioning" -> js.Dictionary(
        "horizontal" -> options.widgetPositioning.map(_._1).getOrElse(UdashDatePicker.Placement.AutoPlacement).name,
        "vertical" -> options.widgetPositioning.map(_._2).getOrElse(UdashDatePicker.Placement.AutoPlacement).name
      )
    )
  }

  private def iconsOptionToJSDict(icons: UdashDatePicker.DatePickerIcons): js.Dictionary[js.Any] = {
    import scalajs.js.JSConverters._
    val dict = js.Dictionary[js.Any]()
    Seq(
      ("time", icons.time),
      ("date", icons.date),
      ("up", icons.up),
      ("down", icons.down),
      ("previous", icons.previous),
      ("next", icons.next),
      ("today", icons.today),
      ("clear", icons.clear),
      ("close", icons.close)
    ).filter(_._2.nonEmpty).foreach(item =>
      dict.update(item._1, item._2.map(_.cls).toJSArray)
    )
    dict
  }

  private def tooltipsOptionToJSDict(tooltips: UdashDatePicker.DatePickerTooltips): js.Dictionary[js.Any] = {
    js.Dictionary[js.Any](
      "today" -> tooltips.today,
      "clear" -> tooltips.clear,
      "close" -> tooltips.close,
      "selectMonth" -> tooltips.selectMonth,
      "prevMonth" -> tooltips.prevMonth,
      "nextMonth" -> tooltips.nextMonth,
      "selectYear" -> tooltips.selectYear,
      "prevYear" -> tooltips.prevYear,
      "nextYear" -> tooltips.nextYear,
      "selectDecade" -> tooltips.selectDecade,
      "prevDecade" -> tooltips.prevDecade,
      "nextDecade" -> tooltips.nextDecade,
      "prevCentury" -> tooltips.prevCentury,
      "nextCentury" -> tooltips.nextCentury
    )
  }

  private def dateToMomentString(date: ju.Date): String =
    Try(moment(options.get.locale.getOrElse("en"), date.getTime, "x").format(options.get.format)).getOrElse(null)

  private def dateToMoment(date: ju.Date): MomentFormatWrapper =
    Try(moment(options.get.locale.getOrElse("en"), date.getTime, "x")).getOrElse(null)

  private def momentStringToDate(date: String): ju.Date =
    Try(new ju.Date(moment(options.get.locale.getOrElse("en"), date, options.get.format).valueOf().toLong)).getOrElse(null)

  private def momentToDate(date: MomentFormatWrapper): ju.Date =
    Try(new ju.Date(date.valueOf().toLong)).getOrElse(null)
}

object UdashDatePicker {
  import scalatags.JsDom.all._

  /** Creates date picker component. */
  def apply(componentId: ComponentId = UdashBootstrap.newId())(date: Property[ju.Date], options: ReadableProperty[UdashDatePicker.DatePickerOptions]): UdashDatePicker =
    new UdashDatePicker(date, options, componentId)

  /** Creates date range selector from provided date pickers. */
  def dateRange(from: UdashDatePicker, to: UdashDatePicker)(fromOptions: Property[UdashDatePicker.DatePickerOptions],
                                                            toOptions: Property[UdashDatePicker.DatePickerOptions]): Registration = {
    val r1 = from.date.streamTo(toOptions)(d => toOptions.get.copy(minDate = Option(d)))
    val r2 = to.date.streamTo(fromOptions)(d => fromOptions.get.copy(maxDate = Option(d)))
    new Registration {
      override def cancel(): Unit = {
        r1.cancel()
        r2.cancel()
      }
    }
  }

  /** Loads Bootstrap Date Picker styles. */
  def loadBootstrapDatePickerStyles(): dom.Element =
    link(rel := "stylesheet", href := "https://cdnjs.cloudflare.com/ajax/libs/bootstrap-datetimepicker/4.17.42/css/bootstrap-datetimepicker.min.css").render

  sealed trait DatePickerEvent extends ListenableEvent[UdashDatePicker]
  object DatePickerEvent {
    case class Show(source: UdashDatePicker) extends DatePickerEvent
    case class Hide(source: UdashDatePicker, date: ju.Date) extends DatePickerEvent
    case class Change(source: UdashDatePicker, date: ju.Date, oldDate: ju.Date) extends DatePickerEvent
    case class Error(source: UdashDatePicker, date: ju.Date) extends DatePickerEvent
  }

  /**
    * Full docs: <a href="http://eonasdan.github.io/bootstrap-datetimepicker/Options/">here</a>.
    * @param format See <a href="http://momentjs.com/docs/#/displaying/format/">momentjs'</a> docs for valid formats.
    *               Format also dictates what components are shown, e.g. MM/dd/YYYY will not display the time picker.
    * @param dayViewHeaderFormat Changes the heading of the datepicker when in "days" view.
    * @param extraFormats Allows for several input formats to be valid.
    * @param stepping Number of minutes the up/down arrow's will move the minutes value in the time picker.
    * @param minDate Prevents date/time selections before this date.
    * @param maxDate Prevents date/time selections after this date.
    * @param useCurrent On show, will set the picker to the current date/time.
    * @param collapse Using a Bootstraps collapse to switch between date/time pickers.
    * @param locale See <a href="http://momentjs.com/docs/#/displaying/format/">momentjs'</a> docs for valid locales.
    * @param defaultDate Sets the picker default date/time. Overrides `useCurrent`.
    * @param disabledDates Disables selection of dates in the array, e.g. holidays.
    * @param enabledDates Disables selection of dates NOT in the array, e.g. holidays.
    * @param icons Change the default icons for the pickers functions.
    * @param useStrict Defines if moment should use strict date parsing when considering a date to be valid.
    * @param sideBySide Shows the picker side by side when using the time and date together.
    * @param daysOfWeekDisabled Disables the section of days of the week, e.g. weekends.
    * @param calendarWeeks Shows the week of the year to the left of first day of the week.
    * @param viewMode The default view to display when the picker is shown.
    *                 Note: To limit the picker to selecting, for instance the year and month, use format: `MM/YYYY`
    * @param toolbarPlacement Changes the placement of the icon toolbar.
    * @param showTodayButton Show the "Today" button in the icon toolbar.
    * @param showClear Show the "Clear" button in the icon toolbar.
    * @param showClose Show the "Close" button in the icon toolbar.
    * @param widgetPositioning Position of datepicker widget.
    * @param widgetParent On picker show, places the widget at the identifier object if the element has css position: 'relative'.
    * @param keepOpen Will cause the date picker to stay open after selecting a date if no time components are being used.
    * @param inline Will display the picker inline without the need of a input field. This will also hide borders and shadows.
    * @param keepInvalid Will cause the date picker to not revert or overwrite invalid dates.
    * @param ignoreReadonly Allow date picker show event to fire even when the associated input element has the `readonly="readonly"` property.
    * @param allowInputToggle If `true`, the picker will show on textbox focus and icon click when used in a button group.
    * @param focusOnShow If `false`, the textbox will not be given focus when the picker is shown
    * @param enabledHours Will allow or disallow hour selections.
    * @param disabledHours Will allow or disallow hour selections.
    * @param viewDate This will change the viewDate without changing or setting the selected date.
    * @param tooltips This will change the tooltips over each icon to a custom string.
    */
  case class DatePickerOptions(format: String,
                               dayViewHeaderFormat: String = "MMMM YYYY",
                               extraFormats: Seq[String] = Seq.empty,
                               stepping: Int = 1,
                               minDate: Option[ju.Date] = None,
                               maxDate: Option[ju.Date] = None,
                               useCurrent: Boolean = true,
                               collapse: Boolean = true,
                               locale: Option[String] = None,
                               defaultDate: Option[ju.Date] = None,
                               disabledDates: Seq[ju.Date] = Seq.empty,
                               enabledDates: Seq[ju.Date] = Seq.empty,
                               icons: DatePickerIcons = DatePickerIcons(),
                               useStrict: Boolean = false,
                               sideBySide: Boolean = false,
                               daysOfWeekDisabled: Seq[DayOfWeek] = Seq.empty,
                               calendarWeeks: Boolean = false,
                               viewMode: ViewMode = ViewMode.Days,
                               toolbarPlacement: Option[UdashDatePicker.Placement.VerticalPlacement] = None,
                               showTodayButton: Boolean = false,
                               showClear: Boolean = false,
                               showClose: Boolean = false,
                               widgetPositioning: Option[(UdashDatePicker.Placement.HorizontalPlacement, UdashDatePicker.Placement.VerticalPlacement)] = None,
                               widgetParent: Option[String] = None,
                               keepOpen: Boolean = false,
                               inline: Boolean = false,
                               keepInvalid: Boolean = false,
                               ignoreReadonly: Boolean = false,
                               allowInputToggle: Boolean = false,
                               focusOnShow: Boolean = true,
                               enabledHours: Seq[Int] = Seq.empty,
                               disabledHours: Seq[Int] = Seq.empty,
                               viewDate: Boolean = false,
                               tooltips: DatePickerTooltips = DatePickerTooltips())

  case class DatePickerIcons(time: Seq[BootstrapClass] = Seq.empty, date: Seq[BootstrapClass] = Seq.empty,
                             up: Seq[BootstrapClass] = Seq.empty, down: Seq[BootstrapClass] = Seq.empty,
                             previous: Seq[BootstrapClass] = Seq.empty, next: Seq[BootstrapClass] = Seq.empty,
                             today: Seq[BootstrapClass] = Seq.empty, clear: Seq[BootstrapClass] = Seq.empty,
                             close: Seq[BootstrapClass] = Seq.empty)

  case class DatePickerTooltips(today: String = "Go to today",
                                clear: String = "Clear selection",
                                close: String = "Close the picker",
                                selectMonth: String = "Select Month",
                                prevMonth: String = "Previous Month",
                                nextMonth: String = "Next Month",
                                selectYear: String = "Select Year",
                                prevYear: String = "Previous Year",
                                nextYear: String = "Next Year",
                                selectDecade: String = "Select Decade",
                                prevDecade: String = "Previous Decade",
                                nextDecade: String = "Next Decade",
                                prevCentury: String = "Previous Century",
                                nextCentury: String = "Next Century")

  sealed class DayOfWeek(val id: Int)
  object DayOfWeek {
    case object Sunday extends DayOfWeek(0)
    case object Monday extends DayOfWeek(1)
    case object Tuesday extends DayOfWeek(2)
    case object Wednesday extends DayOfWeek(3)
    case object Thursday extends DayOfWeek(4)
    case object Friday extends DayOfWeek(5)
    case object Saturday extends DayOfWeek(6)
  }

  sealed class ViewMode(val id: String)
  object ViewMode {
    case object Days extends ViewMode("days")
    case object Months extends ViewMode("months")
    case object Years extends ViewMode("years")
    case object Decades extends ViewMode("decades")
  }

  sealed abstract class Placement(val name: String)
  object Placement {
    case object DefaultPlacement extends Placement("default")
    case object AutoPlacement extends Placement("auto")

    sealed abstract class VerticalPlacement(name: String) extends Placement(name)
    case object TopPlacement extends VerticalPlacement("top")
    case object BottomPlacement extends VerticalPlacement("bottom")

    sealed abstract class HorizontalPlacement(name: String) extends Placement(name)
    case object LeftPlacement extends HorizontalPlacement("left")
    case object RightPlacement extends HorizontalPlacement("right")
  }

  @js.native
  private trait UdashDatePickerJQuery extends JQuery {
    def datetimepicker(settings: js.Dictionary[js.Any]): UdashDatePickerJQuery = js.native
  }

  private implicit class UdashDatePickerJQueryExt(self: UdashDatePickerJQuery) {
    def dpData(): UdashDatePickerDataJQuery = self.data("DateTimePicker").get.asInstanceOf[UdashDatePickerDataJQuery]
  }

  @js.native
  private trait UdashDatePickerDataJQuery extends JQuery {
    def options(settings: js.Dictionary[js.Any]): UdashDatePickerJQuery = js.native
    def date(formattedDate: String): Unit = js.native

    def show(): Unit = js.native
    def hide(): Unit = js.native
    def toggle(): Unit = js.native
    def enable(): Unit = js.native
    def disable(): Unit = js.native
  }

  private implicit class JQueryDatePickerExt(jQ: JQuery) {
    def asDatePicker(): UdashDatePickerJQuery =
      jQ.asInstanceOf[UdashDatePickerJQuery]
  }

  @js.native
  private trait DatePickerChangeJQEvent extends JQueryEvent {
    def date: MomentFormatWrapper = js.native
    def oldDate: MomentFormatWrapper = js.native
  }

  @js.native
  private trait DatePickerShowJQEvent extends JQueryEvent

  @js.native
  private trait DatePickerHideJQEvent extends JQueryEvent {
    def date: MomentFormatWrapper = js.native
  }

  @js.native
  private trait DatePickerErrorJQEvent extends JQueryEvent {
    def date: MomentFormatWrapper = js.native
  }

  private def moment(locale: String, time: js.Any, format: String): MomentFormatWrapper =
    js.Dynamic.global.moment(time, format, locale).asInstanceOf[MomentFormatWrapper]

  @js.native
  private trait MomentFormatWrapper extends js.Any {
    def format(dateFormat: String): String = js.native
    def valueOf(): Double = js.native
  }
}