package com.mockdata.zipfit.workspace.dto;

import jakarta.validation.constraints.NotNull;

/** MEM-04 새 호스트가 될 멤버 */
public record DelegateHostRequest(@NotNull Long userId) {
}
