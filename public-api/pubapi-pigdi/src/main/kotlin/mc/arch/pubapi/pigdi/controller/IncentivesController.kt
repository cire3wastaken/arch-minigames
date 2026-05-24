package mc.arch.pubapi.pigdi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import mc.arch.pubapi.pigdi.dto.AdConfirmResponse
import mc.arch.pubapi.pigdi.dto.AdConfirmTooEarlyResponse
import mc.arch.pubapi.pigdi.dto.ErrorResponse
import mc.arch.pubapi.pigdi.dto.GenerateAdLinkResponse
import mc.arch.pubapi.pigdi.service.ConfirmOutcome
import mc.arch.pubapi.pigdi.service.IncentivesService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*
import kotlin.math.max

@RestController
@RequestMapping("/v1/incentives")
@Tag(name = "Incentives", description = "Ad-watch incentive endpoints")
class IncentivesController(
    private val incentivesService: IncentivesService
)
{
    // this will run from in-game to generate a url where they can watch ads from
    @GetMapping("/generate-ad-link/{uuid}")
    @Operation(
        summary = "Generate a single-use ad link for a player",
        description = "Returns a URL pointing at the ad landing page that is specific to a player. " +
            "Generating a new link rotates the player's current ad nonce — any previously issued " +
            "payload is invalidated."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Ad link generated",
                content = [Content(schema = Schema(implementation = GenerateAdLinkResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid UUID format",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid or missing API key",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    fun generateAdLink(
        @Parameter(description = "Minecraft UUID of the player who will watch the ad")
        @PathVariable uuid: UUID
    ): ResponseEntity<Any>
    {
        val result = incentivesService.generateAdLink(uuid)
        return ResponseEntity.ok(
            GenerateAdLinkResponse(url = result.url, payload = result.payload)
        )
    }

    // confirms their ad watch via their payload, rotates to a fresh nonce so they can chain
    @PostMapping("/ad-confirm")
    @Operation(
        summary = "Confirm an ad watch",
        description = "Decodes the submitted payload, checks it against the player's current ad " +
            "nonce, enforces a minimum interval since the previous confirm, and on success rotates " +
            "to a fresh nonce returned in the response so the ads page can chain into the next ad."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Watch confirmed; nextPayload/nextUrl chain to the next ad",
                content = [Content(schema = Schema(implementation = AdConfirmResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Payload is malformed",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid or missing API key",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "No AKERS profile for the encoded player UUID",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Submitted nonce no longer matches the current expected nonce",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "429",
                description = "Dwell time since the previous confirm has not elapsed",
                content = [Content(schema = Schema(implementation = AdConfirmTooEarlyResponse::class))]
            )
        ]
    )
    fun confirmAdWatch(
        @Parameter(description = "Base64 payload returned by /generate-ad-link or a previous /ad-confirm")
        @RequestParam payload: String
    ): ResponseEntity<Any>
    {
        if (payload.isBlank())
        {
            return ResponseEntity.badRequest().body(
                ErrorResponse(
                    error = "INVALID_PAYLOAD",
                    message = "Payload must not be blank"
                )
            )
        }

        return when (val outcome = incentivesService.confirmAdWatch(payload))
        {
            is ConfirmOutcome.Success -> ResponseEntity.ok(
                AdConfirmResponse(
                    success = true,
                    uuid = outcome.uuid,
                    totalWatches = outcome.totalWatches,
                    nextPayload = outcome.nextPayload,
                    nextUrl = outcome.nextUrl,
                    nextAllowedAt = outcome.nextAllowedAt
                )
            )

            ConfirmOutcome.InvalidPayload -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    error = "INVALID_PAYLOAD",
                    message = "Payload could not be decoded"
                )
            )

            ConfirmOutcome.ProfileNotFound -> ResponseEntity.status(404).body(
                ErrorResponse(
                    error = "PROFILE_NOT_FOUND",
                    message = "No AKERS profile exists for the encoded UUID"
                )
            )

            ConfirmOutcome.NonceInvalid -> ResponseEntity.status(409).body(
                ErrorResponse(
                    error = "NONCE_INVALID",
                    message = "The submitted nonce is not the current expected nonce. Request a fresh ad link."
                )
            )

            is ConfirmOutcome.TooEarly ->
            {
                val retryAfterSeconds = max(1L, (outcome.retryAfterMs + 999L) / 1000L)
                ResponseEntity.status(429)
                    .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
                    .body(
                        AdConfirmTooEarlyResponse(
                            error = "TOO_EARLY",
                            message = "Dwell time since previous confirm has not elapsed",
                            nextAllowedAt = outcome.nextAllowedAt,
                            retryAfterMs = outcome.retryAfterMs
                        )
                    )
            }
        }
    }
}
