/*
 * (C) Copyright IBM Deutschland GmbH 2021
 * (C) Copyright IBM Corp. 2021
 */

package de.rki.covpass.checkapp.scanner

import com.ensody.reactivestate.BaseReactiveState
import com.ibm.health.common.android.utils.BaseEvents
import de.rki.covpass.checkapp.validation.ValidationResult2gData
import de.rki.covpass.checkapp.validitycheck.CovPassCheckValidationResult
import de.rki.covpass.sdk.cert.models.CovCertificate
import de.rki.covpass.sdk.cert.models.Recovery
import de.rki.covpass.sdk.cert.models.TestCert
import de.rki.covpass.sdk.cert.models.Vaccination
import de.rki.covpass.sdk.utils.formatDateFromString
import kotlinx.coroutines.CoroutineScope
import java.time.ZonedDateTime

/**
 * Interface to communicate events from [CovPassCheckQRScannerDataViewModel] to [CovPassCheckQRScannerFragment].
 */
internal interface CovPassCheckQRScannerDataEvents : BaseEvents {
    fun on2gData(certData: ValidationResult2gData?, testData: ValidationResult2gData?, certFirst: Boolean)
    fun on3gSuccess(certificate: CovCertificate)
    fun on3gValidPcrTest(certificate: CovCertificate, sampleCollection: ZonedDateTime?)
    fun on3gValidAntigenTest(certificate: CovCertificate, sampleCollection: ZonedDateTime?)
    fun on3gTechnicalFailure(is2gOn: Boolean = false)
    fun on3gFailure(is2gOn: Boolean = false)
    fun showWarning2gUnexpectedType()
}

/**
 * ViewModel holding the data preparation for both 2g+ and 3g check.
 */
internal class CovPassCheckQRScannerDataViewModel constructor(
    scope: CoroutineScope,
    private val isTwoGOn: Boolean,
) : BaseReactiveState<CovPassCheckQRScannerDataEvents>(scope) {

    var certificateData2G: ValidationResult2gData? = null
    var testCertificateData2G: ValidationResult2gData? = null

    fun prepareDataOnSuccess(certificate: CovCertificate) {
        if (isTwoGOn) {
            if (isNewCertificateValid(certificateData2G, certificate)) {
                eventNotifier {
                    on2gData(
                        ValidationResult2gData(
                            certificate.fullName,
                            certificate.fullTransliteratedName,
                            formatDateFromString(certificate.birthDateFormatted),
                            null,
                            CovPassCheckValidationResult.Success,
                            certificate.dgcEntry.id
                        ),
                        testCertificateData2G,
                        false
                    )
                }
            } else {
                show2GError()
            }
        } else {
            eventNotifier {
                on3gSuccess(certificate)
            }
        }
    }

    fun prepareDataOnValidPcrTest(certificate: CovCertificate, sampleCollection: ZonedDateTime?) {
        if (isTwoGOn) {
            if (isNewCertificateValid(testCertificateData2G, certificate)) {
                eventNotifier {
                    on2gData(
                        certificateData2G,
                        ValidationResult2gData(
                            certificate.fullName,
                            certificate.fullTransliteratedName,
                            formatDateFromString(certificate.birthDateFormatted),
                            sampleCollection,
                            CovPassCheckValidationResult.Success,
                            certificate.dgcEntry.id
                        ),
                        true
                    )
                }
            } else {
                show2GError()
            }
        } else {
            eventNotifier {
                on3gValidPcrTest(certificate, sampleCollection)
            }
        }
    }

    fun prepareDataOnValidAntigenTest(certificate: CovCertificate, sampleCollection: ZonedDateTime?) {
        if (isTwoGOn) {
            if (isNewCertificateValid(testCertificateData2G, certificate)) {
                eventNotifier {
                    on2gData(
                        certificateData2G,
                        ValidationResult2gData(
                            certificate.fullName,
                            certificate.fullTransliteratedName,
                            formatDateFromString(certificate.birthDateFormatted),
                            sampleCollection,
                            CovPassCheckValidationResult.Success,
                            certificate.dgcEntry.id
                        ),
                        true
                    )
                }
            } else {
                show2GError()
            }
        } else {
            eventNotifier {
                on3gValidAntigenTest(certificate, sampleCollection)
            }
        }
    }

    fun prepareDataOnValidationFailure(isTechnical: Boolean, certificate: CovCertificate?) {
        val validationResult = if (isTechnical) {
            CovPassCheckValidationResult.TechnicalError
        } else {
            CovPassCheckValidationResult.ValidationError
        }
        if (isTwoGOn) {
            if (certificate != null) {
                prepareData2g(certificate, validationResult)
            } else {
                prepareData2gNullCert(isTechnical, validationResult)
            }
        } else {
            prepareDataOnValidationFailure3g(isTechnical)
        }
    }

    fun compareData(certData: ValidationResult2gData?, testData: ValidationResult2gData?) =
        if (isDataNotNull(certData, testData)) {
            when {
                certData?.certificateTransliteratedName != testData?.certificateTransliteratedName &&
                    certData?.certificateBirthDate == testData?.certificateBirthDate -> {
                    DataComparison.NameDifferent
                }
                certData?.certificateBirthDate != testData?.certificateBirthDate -> {
                    DataComparison.DateOfBirthDifferent
                }
                else -> {
                    DataComparison.Equal
                }
            }
        } else {
            DataComparison.HasNullData
        }

    private fun isDataNotNull(certData: ValidationResult2gData?, testData: ValidationResult2gData?) =
        certData != null && testData != null &&
            certData.certificateTransliteratedName != null &&
            testData.certificateTransliteratedName != null &&
            certData.certificateBirthDate != null &&
            testData.certificateBirthDate != null

    private fun prepareData2g(certificate: CovCertificate, validationResult: CovPassCheckValidationResult) {
        when (certificate.dgcEntry) {
            is Recovery,
            is Vaccination,
            -> {
                if (isNewCertificateValid(certificateData2G, certificate)) {
                    eventNotifier {
                        on2gData(
                            ValidationResult2gData(
                                certificate.fullName,
                                certificate.fullTransliteratedName,
                                formatDateFromString(certificate.birthDateFormatted),
                                null,
                                validationResult,
                                certificate.dgcEntry.id
                            ),
                            testCertificateData2G,
                            false
                        )
                    }
                } else {
                    show2GError()
                }
            }
            is TestCert -> {
                if (isNewCertificateValid(testCertificateData2G, certificate)) {
                    eventNotifier {
                        on2gData(
                            certificateData2G,
                            ValidationResult2gData(
                                certificate.fullName,
                                certificate.fullTransliteratedName,
                                formatDateFromString(certificate.birthDateFormatted),
                                null,
                                validationResult,
                                certificate.dgcEntry.id
                            ),
                            true
                        )
                    }
                } else {
                    show2GError()
                }
            }
        }
    }

    private fun prepareData2gNullCert(isTechnical: Boolean, validationResult: CovPassCheckValidationResult) {
        when {
            certificateData2G == null && testCertificateData2G == null && isTechnical -> {
                eventNotifier {
                    on3gTechnicalFailure(true)
                }
            }
            certificateData2G == null && testCertificateData2G == null && !isTechnical -> {
                eventNotifier {
                    on3gFailure(true)
                }
            }
            certificateData2G != null -> {
                eventNotifier {
                    on2gData(
                        certificateData2G,
                        ValidationResult2gData(
                            null,
                            null,
                            null,
                            null,
                            validationResult,
                            null
                        ),
                        true
                    )
                }
            }
            testCertificateData2G != null -> {
                eventNotifier {
                    on2gData(
                        ValidationResult2gData(
                            null,
                            null,
                            null,
                            null,
                            validationResult,
                            null
                        ),
                        testCertificateData2G,
                        false
                    )
                }
            }
        }
    }

    private fun prepareDataOnValidationFailure3g(isTechnical: Boolean) {
        if (isTechnical) {
            eventNotifier {
                on3gTechnicalFailure()
            }
        } else {
            eventNotifier {
                on3gFailure()
            }
        }
    }

    private fun show2GError() {
        eventNotifier {
            showWarning2gUnexpectedType()
        }
    }

    private fun isNewCertificateValid(certificateData: ValidationResult2gData?, certificate: CovCertificate) =
        certificateData == null ||
            (
                certificateData.certificateId != certificate.dgcEntry.id &&
                    certificateData.certificateResult != CovPassCheckValidationResult.Success
                )
}

internal enum class DataComparison {
    Equal, NameDifferent, DateOfBirthDifferent, HasNullData
}
