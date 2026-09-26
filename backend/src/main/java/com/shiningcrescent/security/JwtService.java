package com.shiningcrescent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {
    private final SecretKey hmacKey;
    private final PrivateKey rsaPrivate;
    private final RSAPublicKey rsaPublic;
    private final String kid;
    private final String issuer;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.oidc.issuer:}") String issuer,
            @Value("${app.upload-dir}") String uploadDir) throws Exception {
        this.hmacKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer == null ? "" : issuer.trim().replaceAll("/+$", "");
        KeyPair pair = loadOrCreateRsa(Path.of(uploadDir));
        this.rsaPrivate = pair.getPrivate();
        this.rsaPublic = (RSAPublicKey) pair.getPublic();
        this.kid = keyId(rsaPublic);
    }

    public String issuer() {
        return issuer;
    }

    public long expirationMs() {
        return expirationMs;
    }

    public String issue(String username, List<String> roles) {
        Date now = new Date();
        var builder = Jwts.builder()
                .header().keyId(kid).type("JWT").and()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(rsaPrivate, Jwts.SIG.RS256);
        if (!issuer.isBlank()) {
            builder.issuer(issuer);
        }
        return builder.compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public Map<String, Object> jwks() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", kid);
        key.put("n", toBase64Url(rsaPublic.getModulus()));
        key.put("e", toBase64Url(rsaPublic.getPublicExponent()));
        return Map.of("keys", List.of(key));
    }

    private Claims claims(String token) {
        try {
            return Jwts.parser().verifyWith(rsaPublic).build().parseSignedClaims(token).getPayload();
        } catch (Exception rsaFailed) {
            return Jwts.parser().verifyWith(hmacKey).build().parseSignedClaims(token).getPayload();
        }
    }

    private static KeyPair loadOrCreateRsa(Path uploadDir) throws Exception {
        Path dir = uploadDir.resolve("oidc");
        Files.createDirectories(dir);
        Path privPath = dir.resolve("rsa-private.der");
        Path pubPath = dir.resolve("rsa-public.der");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (Files.exists(privPath) && Files.exists(pubPath)) {
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privPath)));
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pubPath)));
            return new KeyPair(pub, priv);
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        Files.write(privPath, pair.getPrivate().getEncoded());
        Files.write(pubPath, pair.getPublic().getEncoded());
        return pair;
    }

    private static String keyId(RSAPublicKey publicKey) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(publicKey.getEncoded());
        return HexFormat.of().formatHex(hash).substring(0, 16);
    }

    private static String toBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            bytes = unsigned;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
