using System.IO;
using System.Net;
using System.Net.Sockets;

namespace CompartirArchivosRED.Net;

public sealed class IncomingInfo
{
    public string SenderName = "";
    public int FileCount;
    public long TotalSize;
    public string Pin = "";
}

/// <summary>Servidor de recepción (TCP 45455) + cliente de envío. Ver PROTOCOL.md.</summary>
public sealed class TransferService : IDisposable
{
    private readonly string _selfName;
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    public string DownloadFolder { get; private set; }

    public event Action<string>? Log;
    public event Action<double>? Progress;              // 0..1
    public event Action<IncomingInfo>? IncomingStarted; // mostrar PIN al usuario
    public event Action<bool>? IncomingFinished;        // éxito?

    private static string DefaultFolder => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        "Downloads", "CompartirArchivosRED");

    private static string SettingsFile => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "CompartirArchivosRED", "download-folder.txt");

    public TransferService(string selfName)
    {
        _selfName = selfName;
        DownloadFolder = LoadFolder();
        try { Directory.CreateDirectory(DownloadFolder); }
        catch { DownloadFolder = DefaultFolder; Directory.CreateDirectory(DownloadFolder); }
    }

    private static string LoadFolder()
    {
        try
        {
            if (File.Exists(SettingsFile))
            {
                var saved = File.ReadAllText(SettingsFile).Trim();
                if (!string.IsNullOrWhiteSpace(saved)) return saved;
            }
        }
        catch { }
        return DefaultFolder;
    }

    /// <summary>Cambia la carpeta de descargas y la persiste. Devuelve true si OK.</summary>
    public bool ChangeDownloadFolder(string path)
    {
        try
        {
            Directory.CreateDirectory(path);
            DownloadFolder = path;
            Directory.CreateDirectory(Path.GetDirectoryName(SettingsFile)!);
            File.WriteAllText(SettingsFile, path);
            Log?.Invoke($"Carpeta de descargas: {path}");
            return true;
        }
        catch (Exception ex)
        {
            Log?.Invoke("No se pudo cambiar la carpeta: " + ex.Message);
            return false;
        }
    }

    public void StartServer()
    {
        _cts = new CancellationTokenSource();
        _listener = new TcpListener(IPAddress.Any, Proto.TransferPort);
        _listener.Start();
        Task.Run(() => AcceptLoop(_cts.Token));
    }

    private async Task AcceptLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                var client = await _listener!.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => HandleIncoming(client));
            }
            catch (OperationCanceledException) { break; }
            catch { }
        }
    }

    private void HandleIncoming(TcpClient client)
    {
        bool ok = false;
        bool announced = false;
        try
        {
            using (client)
            using (var stream = client.GetStream())
            {
                var f = new Framed(stream);
                var offer = Msg.FromJson(f.ReadLine() ?? "");
                if (offer == null || offer.Type != Proto.OFFER) return;

                string pin = Random.Shared.Next(0, 1_000_000).ToString("D6");
                var info = new IncomingInfo
                {
                    SenderName = offer.Name ?? "?",
                    FileCount = offer.FileCount ?? 0,
                    TotalSize = offer.TotalSize ?? 0,
                    Pin = pin
                };
                announced = true;
                IncomingStarted?.Invoke(info);
                Log?.Invoke($"Solicitud de «{info.SenderName}» ({info.FileCount} archivo/s). PIN: {pin}");

                var expires = DateTime.UtcNow.AddSeconds(60);
                f.WriteLine(new Msg { Type = Proto.PIN_REQUIRED }.ToJson());

                var pinMsg = Msg.FromJson(f.ReadLine() ?? "");
                if (pinMsg == null || pinMsg.Type != Proto.PIN)
                { f.WriteLine(new Msg { Type = Proto.REJECTED, Reason = "denied" }.ToJson()); return; }
                if (DateTime.UtcNow > expires)
                { f.WriteLine(new Msg { Type = Proto.REJECTED, Reason = "expired" }.ToJson()); Log?.Invoke("PIN expirado."); return; }
                if (pinMsg.Pin != pin)
                { f.WriteLine(new Msg { Type = Proto.REJECTED, Reason = "bad_pin" }.ToJson()); Log?.Invoke("PIN incorrecto."); return; }

                f.WriteLine(new Msg { Type = Proto.ACCEPTED }.ToJson());

                long total = info.TotalSize > 0 ? info.TotalSize : 1;
                long done = 0;
                while (true)
                {
                    var line = f.ReadLine();
                    if (line == null) break;
                    var m = Msg.FromJson(line);
                    if (m == null) break;
                    if (m.Type == Proto.DONE) { ok = true; break; }
                    if (m.Type != Proto.FILE) continue;

                    string name = SanitizeName(m.Name ?? "archivo");
                    long size = m.Size ?? 0;
                    string dest = UniquePath(Path.Combine(DownloadFolder, name));
                    long baseDone = done;
                    using (var fs = File.Create(dest))
                    {
                        f.CopyExact(fs, size, written =>
                            Progress?.Invoke(Math.Min(1.0, (baseDone + written) / (double)total)));
                    }
                    done += size;
                    Log?.Invoke($"Recibido: {name} ({FormatSize(size)})");
                }
            }
        }
        catch (Exception ex) { Log?.Invoke("Error en recepción: " + ex.Message); }
        finally
        {
            Progress?.Invoke(0);
            if (announced) IncomingFinished?.Invoke(ok);
        }
    }

    public async Task<bool> SendFilesAsync(Peer peer, IEnumerable<string> paths, Func<Task<string?>> pinProvider)
    {
        var files = paths.Where(File.Exists).ToList();
        if (files.Count == 0) { Log?.Invoke("No hay archivos válidos que enviar."); return false; }
        long totalSize = files.Sum(p => new FileInfo(p).Length);

        try
        {
            using var client = new TcpClient();
            await client.ConnectAsync(peer.Address, peer.Port);
            using var stream = client.GetStream();
            var f = new Framed(stream);

            f.WriteLine(new Msg
            {
                Type = Proto.OFFER,
                Name = _selfName,
                FileCount = files.Count,
                TotalSize = totalSize
            }.ToJson());

            var req = Msg.FromJson(f.ReadLine() ?? "");
            if (req == null || req.Type != Proto.PIN_REQUIRED)
            { Log?.Invoke("El receptor no solicitó PIN."); return false; }

            string? pin = await pinProvider();
            if (string.IsNullOrWhiteSpace(pin)) { Log?.Invoke("Envío cancelado."); return false; }
            f.WriteLine(new Msg { Type = Proto.PIN, Pin = pin!.Trim() }.ToJson());

            var resp = Msg.FromJson(f.ReadLine() ?? "");
            if (resp == null || resp.Type != Proto.ACCEPTED)
            {
                Log?.Invoke("Rechazado por el receptor: " + (resp?.Reason ?? "desconocido"));
                return false;
            }

            long done = 0;
            long total = totalSize > 0 ? totalSize : 1;
            foreach (var path in files)
            {
                var fi = new FileInfo(path);
                f.WriteLine(new Msg { Type = Proto.FILE, Name = fi.Name, Size = fi.Length }.ToJson());
                long baseDone = done;
                using (var fs = File.OpenRead(path))
                {
                    f.WriteBytes(fs, fi.Length, sent =>
                        Progress?.Invoke(Math.Min(1.0, (baseDone + sent) / (double)total)));
                }
                done += fi.Length;
                Log?.Invoke($"Enviado: {fi.Name} ({FormatSize(fi.Length)})");
            }
            f.WriteLine(new Msg { Type = Proto.DONE }.ToJson());
            Log?.Invoke("Transferencia completada.");
            return true;
        }
        catch (Exception ex) { Log?.Invoke("Error en envío: " + ex.Message); return false; }
        finally { Progress?.Invoke(0); }
    }

    private static string SanitizeName(string name)
    {
        name = Path.GetFileName(name);
        foreach (var c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
        return string.IsNullOrWhiteSpace(name) ? "archivo" : name;
    }

    private static string UniquePath(string path)
    {
        if (!File.Exists(path)) return path;
        string dir = Path.GetDirectoryName(path)!;
        string name = Path.GetFileNameWithoutExtension(path);
        string ext = Path.GetExtension(path);
        int i = 1;
        string candidate;
        do { candidate = Path.Combine(dir, $"{name} ({i}){ext}"); i++; }
        while (File.Exists(candidate));
        return candidate;
    }

    public static string FormatSize(long bytes)
    {
        string[] u = { "B", "KB", "MB", "GB", "TB" };
        double v = bytes; int i = 0;
        while (v >= 1024 && i < u.Length - 1) { v /= 1024; i++; }
        return $"{v:0.#} {u[i]}";
    }

    public void Dispose()
    {
        try { _cts?.Cancel(); } catch { }
        try { _listener?.Stop(); } catch { }
    }
}
