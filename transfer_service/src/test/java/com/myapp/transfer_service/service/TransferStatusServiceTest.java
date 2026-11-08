package com.myapp.transfer_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myapp.transfer_service.model.Status;
import com.myapp.transfer_service.model.Transfer;
import com.myapp.transfer_service.repository.TransferRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferStatusServiceTest {
    private final TransferRepository repository = mock(TransferRepository.class);
    private final TransferStatusService service = new TransferStatusService(repository);

    @ParameterizedTest
    @EnumSource(
            value = Status.class,
            names = {"COMPLETED", "FAILED"})
    void terminalStateCannotBeOverwritten(Status status) {
        UUID id = UUID.randomUUID();
        Transfer transfer =
                Transfer.builder().id(id).status(status).failureReason("original").build();
        when(repository.findById(id)).thenReturn(Optional.of(transfer));
        assertThat(service.markCompleted(id)).isSameAs(transfer);
        assertThat(service.markFailed(id, "new reason")).isSameAs(transfer);
        assertThat(transfer.getStatus()).isEqualTo(status);
        assertThat(transfer.getFailureReason()).isEqualTo("original");
        verify(repository, never()).save(any());
    }
}
