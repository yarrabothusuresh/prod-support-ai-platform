package com.example.prodsupport.database.credentials;

public interface DatabaseCredentialProvider {

    /**
     * Resolves the database password for a given credential reference.
     * Implementations must never log or leak the returned secret.
     *
     * @param credentialReference e.g. "PAYMENT_DB" or "DB_PAYMENT"
     * @return resolved plaintext password for internal DataSource configuration, or null/empty if none
     */
    String resolvePassword(String credentialReference);
}
