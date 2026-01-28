package com.github.inxilpro.chronicle.services

import ai.philterd.phileas.PhileasConfiguration
import ai.philterd.phileas.policy.Identifiers
import ai.philterd.phileas.policy.Policy
import ai.philterd.phileas.policy.filters.BankRoutingNumber
import ai.philterd.phileas.policy.filters.BitcoinAddress
import ai.philterd.phileas.policy.filters.CreditCard
import ai.philterd.phileas.policy.filters.CustomDictionary
import ai.philterd.phileas.policy.filters.EmailAddress
import ai.philterd.phileas.policy.filters.IbanCode
import ai.philterd.phileas.policy.filters.Identifier
import ai.philterd.phileas.policy.filters.IpAddress
import ai.philterd.phileas.policy.filters.MacAddress
import ai.philterd.phileas.policy.filters.PhoneNumber
import ai.philterd.phileas.policy.filters.Ssn
import ai.philterd.phileas.policy.filters.Url
import ai.philterd.phileas.policy.filters.Vin
import ai.philterd.phileas.services.filters.filtering.PlainTextFilterService
import ai.philterd.phileas.services.strategies.custom.CustomDictionaryFilterStrategy
import ai.philterd.phileas.services.strategies.rules.BankRoutingNumberFilterStrategy
import ai.philterd.phileas.services.strategies.rules.BitcoinAddressFilterStrategy
import ai.philterd.phileas.services.strategies.rules.CreditCardFilterStrategy
import ai.philterd.phileas.services.strategies.rules.EmailAddressFilterStrategy
import ai.philterd.phileas.services.strategies.rules.IbanCodeFilterStrategy
import ai.philterd.phileas.services.strategies.rules.IdentifierFilterStrategy
import ai.philterd.phileas.services.strategies.rules.IpAddressFilterStrategy
import ai.philterd.phileas.services.strategies.rules.MacAddressFilterStrategy
import ai.philterd.phileas.services.strategies.rules.PhoneNumberFilterStrategy
import ai.philterd.phileas.services.strategies.rules.SsnFilterStrategy
import ai.philterd.phileas.services.strategies.rules.UrlFilterStrategy
import ai.philterd.phileas.services.strategies.rules.VinFilterStrategy
import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.Properties

/**
 * Service that uses Phileas to redact sensitive information from transcript events.
 * Filters PII, secrets, and credentials before they are logged.
 */
@Service(Service.Level.PROJECT)
class PhileasFilteringService(private val project: Project) {

    private val filterService: PlainTextFilterService
    private val policy: Policy

    init {
        val properties = Properties()
        val config = PhileasConfiguration(properties)
        // Pass null for contextService and vectorService - they're optional for basic filtering
        filterService = PlainTextFilterService(config, null, null)
        policy = createPolicy()
        thisLogger().info("PhileasFilteringService initialized")
    }

    private fun createPolicy(): Policy {
        val identifiers = Identifiers()

        // Credit cards
        val creditCard = CreditCard()
        creditCard.creditCardFilterStrategies = listOf(
            CreditCardFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.creditCard = creditCard

        // SSNs
        val ssn = Ssn()
        ssn.ssnFilterStrategies = listOf(
            SsnFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.ssn = ssn

        // Email addresses
        val email = EmailAddress()
        email.emailAddressFilterStrategies = listOf(
            EmailAddressFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.emailAddress = email

        // IP addresses
        val ipAddress = IpAddress()
        ipAddress.ipAddressFilterStrategies = listOf(
            IpAddressFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.ipAddress = ipAddress

        // Phone numbers
        val phoneNumber = PhoneNumber()
        phoneNumber.phoneNumberFilterStrategies = listOf(
            PhoneNumberFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.phoneNumber = phoneNumber

        // URLs (can contain credentials)
        val url = Url()
        url.urlFilterStrategies = listOf(
            UrlFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.url = url

        // Bank routing numbers
        val bankRoutingNumber = BankRoutingNumber()
        bankRoutingNumber.bankRoutingNumberFilterStrategies = listOf(
            BankRoutingNumberFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.bankRoutingNumber = bankRoutingNumber

        // Bitcoin addresses
        val bitcoinAddress = BitcoinAddress()
        bitcoinAddress.bitcoinFilterStrategies = listOf(
            BitcoinAddressFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.bitcoinAddress = bitcoinAddress

        // IBAN codes
        val ibanCode = IbanCode()
        ibanCode.ibanCodeFilterStrategies = listOf(
            IbanCodeFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.ibanCode = ibanCode

        // MAC addresses
        val macAddress = MacAddress()
        macAddress.macAddressFilterStrategies = listOf(
            MacAddressFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.macAddress = macAddress

        // VINs
        val vin = Vin()
        vin.vinFilterStrategies = listOf(
            VinFilterStrategy().apply { strategy = REDACT_STRATEGY }
        )
        identifiers.vin = vin

        // Custom dictionaries for secret keywords
        val customDictionaries = mutableListOf<CustomDictionary>()

        val secretKeywords = CustomDictionary().apply {
            classification = "secret-keywords"
            customDictionaryFilterStrategies = listOf(
                CustomDictionaryFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            terms = listOf(
                "password",
                "passwd",
                "secret",
                "api_key",
                "apikey",
                "api-key",
                "access_token",
                "auth_token",
                "bearer",
                "private_key"
            )
        }
        customDictionaries.add(secretKeywords)

        identifiers.customDictionaries = customDictionaries

        // Add identifier patterns for AWS keys, GitHub tokens, etc.
        val identifierPatterns = mutableListOf<Identifier>()

        // AWS Access Key ID pattern
        val awsAccessKey = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?:A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}"
            isCaseSensitive = true
            classification = "aws-access-key"
        }
        identifierPatterns.add(awsAccessKey)

        // AWS Secret Access Key pattern
        val awsSecretKey = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?i)aws[_\\-]?secret[_\\-]?access[_\\-]?key[\"']?\\s*[:=]\\s*[\"']?[A-Za-z0-9/+=]{40}"
            isCaseSensitive = false
            classification = "aws-secret-key"
        }
        identifierPatterns.add(awsSecretKey)

        // Generic API key pattern
        val genericApiKey = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?i)(?:api[_\\-]?key|apikey|secret[_\\-]?key|auth[_\\-]?token|access[_\\-]?token)[\"']?\\s*[:=]\\s*[\"']?[A-Za-z0-9_\\-]{20,}"
            isCaseSensitive = false
            classification = "api-key"
        }
        identifierPatterns.add(genericApiKey)

        // GitHub token pattern
        val githubToken = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "gh[pousr]_[A-Za-z0-9_]{36,}"
            isCaseSensitive = true
            classification = "github-token"
        }
        identifierPatterns.add(githubToken)

        // Private key headers
        val privateKeyHeader = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----"
            isCaseSensitive = false
            classification = "private-key"
        }
        identifierPatterns.add(privateKeyHeader)

        // Database connection strings with credentials
        val dbConnectionString = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?i)(?:postgresql|mysql|mongodb|redis)://[^:]+:[^@]+@[^\\s]+"
            isCaseSensitive = false
            classification = "db-connection-string"
        }
        identifierPatterns.add(dbConnectionString)

        // Password in command line arguments
        val passwordArg = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?i)(?:-p|--password[=\\s]|password[=:\\s])[\"']?[^\\s\"']{4,}"
            isCaseSensitive = false
            classification = "password-arg"
        }
        identifierPatterns.add(passwordArg)

        // Bearer tokens
        val bearerToken = Identifier().apply {
            identifierFilterStrategies = listOf(
                IdentifierFilterStrategy().apply {
                    strategy = REDACT_STRATEGY
                }
            )
            pattern = "(?i)bearer\\s+[A-Za-z0-9_\\-\\.]+={0,2}"
            isCaseSensitive = false
            classification = "bearer-token"
        }
        identifierPatterns.add(bearerToken)

        identifiers.identifiers = identifierPatterns

        return Policy().apply {
            name = "chronicle-secrets-policy"
            this.identifiers = identifiers
        }
    }

    /**
     * Filters sensitive information from the given text.
     * Returns the redacted text.
     */
    fun filterText(text: String): String {
        if (text.isBlank()) return text

        return try {
            val result = filterService.filter(policy, "chronicle", text)
            result.filteredText
        } catch (e: Exception) {
            thisLogger().warn("Failed to filter text: ${e.message}")
            text
        }
    }

    /**
     * Filters sensitive information from a TranscriptEvent.
     * Returns a new event with redacted content, or the original if no filtering needed.
     */
    fun filterEvent(event: TranscriptEvent): TranscriptEvent {
        val settings = ChronicleSettings.getInstance(project)
        if (!settings.enableSecretFiltering) return event

        return when (event) {
            is ShellCommandEvent -> event.copy(
                command = filterText(event.command),
                workingDirectory = event.workingDirectory?.let { filterText(it) }
            )
            is SelectionEvent -> event.copy(
                text = event.text?.let { filterText(it) }
            )
            is SearchEvent -> event.copy(
                query = filterText(event.query)
            )
            is AudioTranscriptionEvent -> event.copy(
                transcriptionText = filterText(event.transcriptionText)
            )
            is RefactoringEvent -> event.copy(
                details = filterText(event.details)
            )
            // These events don't contain sensitive text content
            is FileOpenedEvent,
            is FileClosedEvent,
            is FileSelectedEvent,
            is RecentFileEvent,
            is DocumentChangedEvent,
            is VisibleAreaEvent,
            is FileCreatedEvent,
            is FileDeletedEvent,
            is FileRenamedEvent,
            is FileMovedEvent,
            is BranchChangedEvent,
            is RefactoringUndoEvent -> event
        }
    }

    companion object {
        private const val REDACT_STRATEGY = "REDACT"

        fun getInstance(project: Project): PhileasFilteringService {
            return project.getService(PhileasFilteringService::class.java)
        }
    }
}
