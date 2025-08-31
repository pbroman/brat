package dev.pbroman.brat.core.data.result;

public record Validation(String componentName, String instanceName, String message, ValidationType validationType) {

    @Override
    public String toString() {
        return "Validation " + validationType.getMessage() + ": " + this.componentName + " for instance '" + this.instanceName + "', message: " + this.message;
    }
}
