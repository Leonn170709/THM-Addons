package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import xyz.thm.addon.THMAddon;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;


public class WebhookEncrypt extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public WebhookEncrypt() {
        super(THMAddon.CATEGORY, "WebhookEncrypt", "Encrypts webhooks for use with Highway Builder.");
    }
    private final Setting<String> decryptkey = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-key")
        .description("The encryption key(anything)")
        .defaultValue("MySecureKeyHere123")
        .build()
    );
    private final Setting<String> webhookToEncrypt = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("The Discord webhook URL to be encrypted")
        .defaultValue("https://discord.com/api/webhooks/...")
        .build()
    );
    public String webhook, key, encrypted;


        //Encrypts a webhook URL using AES-256 encryption.

    public String encryptWebhook(String webhook, String password) {
        try {
            // Derive a 256-bit (32 byte) key from the password using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Create AES-256 cipher
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] webhookBytes = webhook.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] encrypted = cipher.doFinal(webhookBytes);

            // Base64 encode
            return java.util.Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            error("Failed to encrypt webhook: (highlight)%", e.getMessage());
            return null;
        }
    }

    @Override
    public void onActivate() {
        webhook = webhookToEncrypt.get();
        key = decryptkey.get();

        if (webhook.isEmpty() || webhook.contains("...") || key.isEmpty()) {
            warning("Webhook or key is empty or placeholder!");
            toggle();
            return;
        }

        encrypted = encryptWebhook(webhook, key);
        if (encrypted != null) {
            info("Encrypted webhook (AES-256): (highlight)%s", encrypted);
            info("Copy this value to the 'encrypted-webhook' setting in Highway Builder");
            info("Use this key in the 'webhook-key' setting: (highlight)%s", key);
        } else {
            error("Failed to encrypt webhook!");
        }

        toggle();
    }
}
