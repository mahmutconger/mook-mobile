package com.mcclabs.mook.feature.billing

import com.mcclabs.mook.domain.billing.BillingError
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.billing_error_activity_unavailable
import mook.shared.generated.resources.billing_error_already_owned
import mook.shared.generated.resources.billing_error_in_progress
import mook.shared.generated.resources.billing_error_network
import mook.shared.generated.resources.billing_error_linked_to_another_account
import mook.shared.generated.resources.billing_error_product_unavailable
import mook.shared.generated.resources.billing_error_unavailable
import mook.shared.generated.resources.billing_error_active_plan_unknown
import mook.shared.generated.resources.billing_error_existing_subscription_not_found
import mook.shared.generated.resources.billing_error_plan_already_active
import mook.shared.generated.resources.billing_error_subscription_managed_elsewhere
import mook.shared.generated.resources.error_generic
import org.jetbrains.compose.resources.getString

/**
 * [BillingError]'ı kullanıcıya gösterilecek yerelleştirilmiş (Türkçe) bir metne çevirir
 * (Gereksinim 1.11).
 *
 * Paywall ve Ayarlar ekranındaki "abonelikleri geri yükle" akışı AYNI hata kümesini
 * paylaştığından, eşleme burada TEK bir yerde tutulur — iki ViewModel'in aynı hata için
 * farklı metinler göstermesi böylece imkânsız hale gelir.
 */
suspend fun BillingError.toLocalizedMessage(): String = when (this) {
    BillingError.BillingUnavailable -> getString(Res.string.billing_error_unavailable)
    BillingError.ItemAlreadyOwned -> getString(Res.string.billing_error_already_owned)
    BillingError.NetworkError -> getString(Res.string.billing_error_network)
    BillingError.OperationInProgress -> getString(Res.string.billing_error_in_progress)
    BillingError.ActivityUnavailable -> getString(Res.string.billing_error_activity_unavailable)
    BillingError.ProductUnavailable -> getString(Res.string.billing_error_product_unavailable)
    BillingError.SubscriptionLinkedToAnotherAccount -> getString(Res.string.billing_error_linked_to_another_account)
    BillingError.ExistingSubscriptionNotFound -> getString(Res.string.billing_error_existing_subscription_not_found)
    BillingError.SubscriptionManagedElsewhere -> getString(Res.string.billing_error_subscription_managed_elsewhere)
    BillingError.PlanAlreadyActive -> getString(Res.string.billing_error_plan_already_active)
    BillingError.ActivePlanUnknown -> getString(Res.string.billing_error_active_plan_unknown)
    is BillingError.Unknown -> rawMessage.ifBlank { getString(Res.string.error_generic) }
}
