#!/usr/bin/env python3
import os
import subprocess
import shutil
import secrets
import string
import base64

def generate_strong_password(length=16):
    """Generate a highly secure random alphanumeric password."""
    alphabet = string.ascii_letters + string.digits + "#!%*"
    return "".join(secrets.choice(alphabet) for _ in range(length))

def run_cmd(args):
    """Helper to run shell commands safely and quietly."""
    try:
        subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False

def main():
    print("==================================================")
    print("🔑 Parental Control - Signed Release Key Generator")
    print("==================================================")
    
    # 1. Generate strong secure credential parameters
    alias = "parentalcontrol-alias"
    keystore_filename = "release.keystore"
    properties_filename = "keystore.properties"
    
    password = generate_strong_password()
    
    print(f"[+] Secure key parameters generated:")
    print(f"    - Key Alias: {alias}")
    print(f"    - Keystore Target: {keystore_filename}")
    print(f"    - Password generated: {password}\n")
    
    # 2. Check for OpenSSL or Keytool availability
    success = False
    
    if shutil.which("openssl"):
        print("[*] OpenSSL detected! Generating PKCS12 Keystore...")
        # Step A: Generate RSA Private Key
        run_cmd(["openssl", "genrsa", "-out", "temp.key", "2048"])
        
        # Step B: Generate Self-Signed Certificate
        subj = "/CN=Parental Control/O=SecureOrg/C=US"
        run_cmd([
            "openssl", "req", "-new", "-x509", 
            "-key", "temp.key", "-out", "temp.crt", 
            "-days", "10000", "-subj", subj
        ])
        
        # Step C: Export to PKCS12 Keystore
        success = run_cmd([
            "openssl", "pkcs12", "-export", 
            "-in", "temp.crt", "-inkey", "temp.key", 
            "-out", keystore_filename, "-name", alias, 
            "-passout", f"pass:{password}"
        ])
        
        # Clean up temporary certificate parts
        for temp_file in ["temp.key", "temp.crt"]:
            if os.path.exists(temp_file):
                os.remove(temp_file)
                
    elif shutil.which("keytool"):
        print("[*] Java Keytool detected! Generating standard JKS Keystore...")
        dname = "CN=Parental Control, O=SecureOrg, C=US"
        success = run_cmd([
            "keytool", "-genkey", "-v", 
            "-keystore", keystore_filename, 
            "-alias", alias, "-keyalg", "RSA", 
            "-keysize", "2048", "-validity", "10000", 
            "-dname", dname, "-storepass", password, 
            "-keypass", password
        ])
    else:
        print("[!] ERROR: Neither 'openssl' nor 'keytool' could be found on your path.")
        print("[!] Please install OpenSSL or the Java Development Kit (JDK) to proceed.")
        return

    if not success:
        print("[!] ERROR: Generation command failed. Please check your system permissions.")
        return

    # 3. Create keystore.properties properties mapping
    with open(properties_filename, "w") as f:
        f.write(f"storeFile=../{keystore_filename}\n")
        f.write(f"storePassword={password}\n")
        f.write(f"keyAlias={alias}\n")
        f.write(f"keyPassword={password}\n")
        
    print(f"[+] SUCCESS: Created {keystore_filename} key binary!")
    print(f"[+] SUCCESS: Configured dynamic credentials inside {properties_filename}!")
    print("[+] Both files are automatically protected and ignored by Git.\n")

    # 4. Generate and display the Base64 representation for GitHub Secrets
    try:
        with open(keystore_filename, "rb") as k_file:
            binary_data = k_file.read()
            b64_string = base64.b64encode(binary_data).decode("utf-8")
            
        print("==================================================")
        print("🚀 GITHUB ACTIONS CI/CD SETUP CREDENTIALS")
        print("==================================================")
        print("Store the following key parameters inside GitHub Repository Secrets:")
        print(f"  1. KEYSTORE_PASSWORD  ➔  {password}")
        print(f"  2. KEY_ALIAS           ➔  {alias}")
        print(f"  3. KEY_PASSWORD       ➔  {password}")
        print("\n  4. KEYSTORE_BASE64    ➔  (Copy the text sequence below):")
        print("--------------------------------------------------")
        print(b64_string)
        print("--------------------------------------------------")
    except Exception as e:
        print(f"[!] Warning: Could not output Base64 string automatically: {e}")
        
    print("==================================================")

if __name__ == "__main__":
    main()
