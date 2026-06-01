package com.example.eventledger.spec;

import com.example.eventledger.dto.BalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Account API specification interface.
 * This interface defines the contract for Account-related endpoints.
 * Controllers should implement this interface to ensure API consistency.
 */
@Tag(name = "Accounts", description = "Account balance operations")
@RequestMapping("/accounts")
public interface AccountApi {

    /**
     * Get account balance.
     * Balance = sum(CREDIT amounts) - sum(DEBIT amounts)
     *
     * @param accountId the account identifier
     * @return the balance response
     */
    @GetMapping("/{accountId}/balance")
    @Operation(
        summary = "Get account balance",
        description = "Calculate and return the current balance for an account. " +
                      "Balance = sum(CREDIT amounts) - sum(DEBIT amounts)"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Balance calculated successfully",
            content = @Content(schema = @Schema(implementation = BalanceResponse.class))
        )
    })
    ResponseEntity<BalanceResponse> getAccountBalance(
        @Parameter(description = "Account ID", required = true)
        @PathVariable("accountId") String accountId
    );
}

