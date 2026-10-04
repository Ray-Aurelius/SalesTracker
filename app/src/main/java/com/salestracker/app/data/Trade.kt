package com.salestracker.app.data

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.salestracker.app.R

/** The words the app uses for the key things, which differ between sales trades. */
data class TradeTerms(
    @StringRes val clients: Int,
    @StringRes val addClient: Int,
    @StringRes val newClient: Int,
    @StringRes val editClient: Int,
    @StringRes val chooseClient: Int,
    @StringRes val noClient: Int,
    @StringRes val logSale: Int,
    @StringRes val editSale: Int,
    @StringRes val saleClosed: Int,
    @StringRes val saleAmount: Int,
    @StringRes val upsellOffered: Int,
    @StringRes val upsellAccepted: Int,
    @StringRes val upsellAmount: Int,
    @StringRes val appointment: Int,
    @StringRes val newAppointment: Int,
    @StringRes val editAppointment: Int,
)

/**
 * Sales trades. Choosing one changes the app's wording (and suggests a starting commission rate),
 * so it feels made for that line of work. Nothing else changes: data is the same in every trade.
 */
enum class Trade(@StringRes val label: Int, @StringRes val example: Int, val suggestedCommission: Double, val terms: TradeTerms) {
    GENERAL(R.string.trade_general, R.string.trade_general_ex, 0.0, TradeTerms(
        R.string.tab_clients, R.string.add_client, R.string.new_client, R.string.edit_client, R.string.client_choose, R.string.client_none,
        R.string.log_sale, R.string.edit_sale, R.string.sale_closed, R.string.sale_amount,
        R.string.upsell_offered, R.string.upsell_accepted, R.string.upsell_amount,
        R.string.appointment_fab, R.string.new_appointment, R.string.edit_appointment,
    )),
    REAL_ESTATE(R.string.trade_realestate, R.string.trade_realestate_ex, 3.0, TradeTerms(
        R.string.tab_clients, R.string.add_client, R.string.new_client, R.string.edit_client, R.string.client_choose, R.string.client_none,
        R.string.t_re_log, R.string.t_re_edit, R.string.t_re_closed, R.string.t_re_amount,
        R.string.t_re_up_offered, R.string.t_re_up_accepted, R.string.t_re_up_amount,
        R.string.t_re_appt, R.string.t_re_appt_new, R.string.t_re_appt_edit,
    )),
    INSURANCE(R.string.trade_insurance, R.string.trade_insurance_ex, 10.0, TradeTerms(
        R.string.tab_clients, R.string.add_client, R.string.new_client, R.string.edit_client, R.string.client_choose, R.string.client_none,
        R.string.t_in_log, R.string.t_in_edit, R.string.t_in_closed, R.string.t_in_amount,
        R.string.t_in_up_offered, R.string.t_in_up_accepted, R.string.t_in_up_amount,
        R.string.t_meeting, R.string.t_meeting_new, R.string.t_meeting_edit,
    )),
    AUTOMOTIVE(R.string.trade_auto, R.string.trade_auto_ex, 0.0, TradeTerms(
        R.string.t_customers, R.string.t_customer_add, R.string.t_customer_new, R.string.t_customer_edit, R.string.t_customer_choose, R.string.t_customer_none,
        R.string.t_au_log, R.string.t_au_edit, R.string.t_au_closed, R.string.t_au_amount,
        R.string.t_au_up_offered, R.string.t_au_up_accepted, R.string.t_au_up_amount,
        R.string.appointment_fab, R.string.new_appointment, R.string.edit_appointment,
    )),
    HOME_SERVICES(R.string.trade_home, R.string.trade_home_ex, 8.0, TradeTerms(
        R.string.t_hs_clients, R.string.t_hs_add, R.string.t_hs_new, R.string.t_hs_edit, R.string.t_hs_choose, R.string.t_hs_none,
        R.string.t_hs_log, R.string.t_hs_edit_sale, R.string.t_hs_closed, R.string.t_hs_amount,
        R.string.t_hs_up_offered, R.string.t_hs_up_accepted, R.string.t_hs_up_amount,
        R.string.t_hs_appt, R.string.t_hs_appt_new, R.string.t_hs_appt_edit,
    )),
    RETAIL(R.string.trade_retail, R.string.trade_retail_ex, 5.0, TradeTerms(
        R.string.t_customers, R.string.t_customer_add, R.string.t_customer_new, R.string.t_customer_edit, R.string.t_customer_choose, R.string.t_customer_none,
        R.string.log_sale, R.string.edit_sale, R.string.sale_closed, R.string.sale_amount,
        R.string.t_rt_up_offered, R.string.t_rt_up_accepted, R.string.t_rt_up_amount,
        R.string.appointment_fab, R.string.new_appointment, R.string.edit_appointment,
    )),
    B2B(R.string.trade_b2b, R.string.trade_b2b_ex, 10.0, TradeTerms(
        R.string.t_bb_clients, R.string.t_bb_add, R.string.t_bb_new, R.string.t_bb_edit, R.string.t_bb_choose, R.string.t_bb_none,
        R.string.t_bb_log, R.string.t_bb_edit_sale, R.string.t_bb_closed, R.string.t_bb_amount,
        R.string.t_bb_up_offered, R.string.t_bb_up_accepted, R.string.t_bb_up_amount,
        R.string.t_meeting, R.string.t_meeting_new, R.string.t_meeting_edit,
    )),
}

/** The current trade's words, available to every screen. */
val LocalTerms = staticCompositionLocalOf { Trade.GENERAL.terms }
