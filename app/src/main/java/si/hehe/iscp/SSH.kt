package si.hehe.iscp

import android.content.SharedPreferences
import android.util.Log
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.xfer.InMemorySourceFile
import org.apache.commons.codec.binary.Base64
import java.io.*
import java.security.*
import java.security.interfaces.RSAPublicKey

class InMemoryKeyProvider (private val key: KeyPair): KeyProvider {

    override fun getType(): KeyType {
        return KeyType.RSA
    }

    override fun getPrivate(): PrivateKey {
        return key.private
    }

    override fun getPublic(): PublicKey {
        return key.public
    }

}

class SSH {

    init {
        Security.addProvider(org.spongycastle.jce.provider.BouncyCastleProvider())
    }

    fun setupSSHAuth(
            host: String,
            port: Int,
            username: String,
            password: String,
            sshKeys: KeyPair,
            uuid: String) {
        val ssh = SSHClient(AndroidConfig())
        val pubKey = extractSSHPublicKey(sshKeys, uuid)
        try {
            ssh.addHostKeyVerifier(PromiscuousVerifier())

            ssh.connect(host, port)
            ssh.authPassword(username, password)

            var session = ssh.startSession()

            // remove old keys
            Log.d("exec-command", """grep iscp-$uuid .ssh/authorized_keys &&
                grep -v iscp-$uuid .ssh/authorized_keys > .ssh/authorized_keys-new &&
                mv .ssh/authorized_keys-new .ssh/authorized_keys""")
            session.exec("""grep iscp-$uuid .ssh/authorized_keys &&
                grep -v iscp-$uuid .ssh/authorized_keys > .ssh/authorized_keys-new &&
                mv .ssh/authorized_keys-new .ssh/authorized_keys""").join()
            session = ssh.startSession()
            // add new key
            session.exec("echo '$pubKey' >> .ssh/authorized_keys").join()
        } finally {
            ssh.disconnect()
            ssh.close()
        }

    }

    fun testSSHConnection(host: String, port: Int, username: String, key: KeyPair) {
        val ssh = SSHClient(AndroidConfig())
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        // ssh.loadKnownHosts()
        ssh.connect(host, port)
        ssh.authPublickey(username, InMemoryKeyProvider(key))
        ssh.disconnect()
    }

    fun generateSSHKeys(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    fun serializeKeys(sshKeys: KeyPair): String {
        val b = ByteArrayOutputStream()
        val o = ObjectOutputStream(b)
        val b64 = Base64()
        o.writeObject(sshKeys)
        return String(b64.encode(b.toByteArray()))
    }

    fun deserializeKeys(serializedKeys: String): KeyPair {
        val b64 = Base64()
        val bi = ByteArrayInputStream(b64.decode(serializedKeys.toByteArray()))
        val oi = ObjectInputStream(bi)
        return oi.readObject() as KeyPair
    }

    private fun extractSSHPublicKey(keyPair: KeyPair, uuid: String): String {
        val pubKey = keyPair.public as RSAPublicKey
        val byteOs = ByteArrayOutputStream()
        val dos = DataOutputStream(byteOs as OutputStream)
        dos.writeInt("ssh-rsa".toByteArray().size)
        dos.write("ssh-rsa".toByteArray())
        dos.writeInt(pubKey.publicExponent.toByteArray().size)
        dos.write(pubKey.publicExponent.toByteArray())
        dos.writeInt(pubKey.modulus.toByteArray().size)
        dos.write(pubKey.modulus.toByteArray())
        val encodedKey = String(Base64.encodeBase64(byteOs.toByteArray()))
        return "ssh-rsa $encodedKey iscp-$uuid"

    }

    fun upload(prefs: SharedPreferences, localSourceFile: InMemorySourceFile, destFileName: String) {
        val ssh = SSHClient(AndroidConfig())
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        // ssh.loadKnownHosts()
        ssh.connect(prefs.getString("hostname", "")!!, prefs.getString("port", "")!!.toInt())
        ssh.authPublickey(
                prefs.getString("username", ""),
                InMemoryKeyProvider(deserializeKeys(prefs.getString("auth_ssh_key", "")!!)))
        val scp = ssh.newSCPFileTransfer().newSCPUploadClient()
        scp.copy(localSourceFile, destFileName)
        ssh.disconnect()
    }
}