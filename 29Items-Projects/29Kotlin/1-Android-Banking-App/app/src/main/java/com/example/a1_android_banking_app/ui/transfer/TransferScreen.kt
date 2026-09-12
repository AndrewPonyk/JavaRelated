package com.example.a1_android_banking_app.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: TransferViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.selectedAccount.collectAsStateWithLifecycle()

    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New transfer", style = MaterialTheme.typography.headlineSmall)

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded && accounts.isNotEmpty(),
            onExpandedChange = { dropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedAccount?.let { "${it.name} · ${it.formattedBalance}" } ?: "No accounts cached",
                onValueChange = {},
                readOnly = true,
                label = { Text("From account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded && accounts.isNotEmpty(),
                onDismissRequest = { dropdownExpanded = false },
            ) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(account.name)
                                Text(
                                    account.formattedBalance,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            viewModel.selectAccount(account.id)
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = form.payeeName,
            onValueChange = viewModel::updatePayeeName,
            label = { Text("Payee name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.payeeIban,
            onValueChange = viewModel::updatePayeeIban,
            label = { Text("Payee IBAN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.amountInput,
            onValueChange = viewModel::updateAmount,
            label = { Text("Amount (${selectedAccount?.currency ?: "EUR"})") },
            isError = viewModel.amountInvalid || viewModel.exceedsBalance,
            supportingText = {
                val message = when {
                    viewModel.amountInvalid -> "Enter a valid amount, e.g. 12.50"
                    viewModel.exceedsBalance -> "Exceeds the account balance"
                    else -> null
                }
                if (message != null) Text(message)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = form.reference,
            onValueChange = viewModel::updateReference,
            label = { Text("Reference (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        TransferStatusArea(
            state = state,
            onReset = viewModel::reset,
            onConfirmBlocked = viewModel::confirmBlocked,
            onDone = onDone,
        )

        Spacer(Modifier.size(32.dp))

        Button(
            onClick = viewModel::submit,
            enabled = viewModel.canSubmit && state !is TransferUiState.Submitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit transfer") }
    }
}

/** Submit outcomes: submitting spinner, fraud block, receipt, or failure message. */
@Composable
private fun TransferStatusArea(
    state: TransferUiState,
    onReset: () -> Unit,
    onConfirmBlocked: () -> Unit,
    onDone: () -> Unit,
) {
    when (val s = state) {
        is TransferUiState.Idle -> Unit

        is TransferUiState.Submitting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text("Submitting…")
        }

        is TransferUiState.Blocked -> Column {
            Text(
                "Transfer flagged for review",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
            s.reasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset) { Text("Cancel") }
                Button(onClick = onConfirmBlocked) { Text("Confirm anyway") }
            }
        }

        is TransferUiState.Success -> Column {
            Text("Transfer completed", color = MaterialTheme.colorScheme.primary)
            Text("${s.transaction.payeeName}: ${s.transaction.id}")
            Button(onClick = onDone) { Text("Back to accounts") }
        }

        is TransferUiState.Failed -> Text(
            s.message,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
