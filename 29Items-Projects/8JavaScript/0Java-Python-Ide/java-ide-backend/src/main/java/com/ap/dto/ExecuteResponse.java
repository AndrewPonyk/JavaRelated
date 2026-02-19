package com.ap.dto;

public class ExecuteResponse {
    private String output;
    private String error;
    private boolean success;

    public ExecuteResponse() {}

    public ExecuteResponse(String output, String error, boolean success) {
        this.output = output;
        this.error = error;
        this.success = success;
    }

    public static ExecuteResponse success(String output) {
        return new ExecuteResponse(output, null, true);
    }

    public static ExecuteResponse failure(String error) {
        return new ExecuteResponse(null, error, false);
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
