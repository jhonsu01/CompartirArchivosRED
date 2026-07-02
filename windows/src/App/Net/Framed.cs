using System.IO;
using System.Text;

namespace CompartirArchivosRED.Net;

/// <summary>
/// Lector/escritor con enmarcado: líneas JSON delimitadas por '\n' intercaladas
/// con bloques de bytes crudos de tamaño conocido. Un búfer interno evita
/// "comerse" bytes de payload al leer una línea.
/// </summary>
public sealed class Framed
{
    private readonly Stream _s;
    private readonly byte[] _buf = new byte[8192];
    private int _start;
    private int _end;

    public Framed(Stream s) => _s = s;

    public string? ReadLine()
    {
        var sb = new List<byte>(128);
        while (true)
        {
            if (_start >= _end)
            {
                _end = _s.Read(_buf, 0, _buf.Length);
                _start = 0;
                if (_end <= 0)
                    return sb.Count == 0 ? null : Encoding.UTF8.GetString(sb.ToArray());
            }
            byte b = _buf[_start++];
            if (b == (byte)'\n') return Encoding.UTF8.GetString(sb.ToArray());
            if (b != (byte)'\r') sb.Add(b);
        }
    }

    public void CopyExact(Stream dest, long count, Action<long> onProgress)
    {
        long remaining = count;
        while (remaining > 0)
        {
            if (_start >= _end)
            {
                _end = _s.Read(_buf, 0, _buf.Length);
                _start = 0;
                if (_end <= 0) throw new IOException("Conexión cerrada durante la recepción.");
            }
            int avail = _end - _start;
            int take = (int)Math.Min(avail, remaining);
            dest.Write(_buf, _start, take);
            _start += take;
            remaining -= take;
            onProgress(count - remaining);
        }
    }

    public void WriteLine(string s)
    {
        byte[] data = Encoding.UTF8.GetBytes(s + "\n");
        _s.Write(data, 0, data.Length);
        _s.Flush();
    }

    public void WriteBytes(Stream src, long count, Action<long> onProgress)
    {
        byte[] tmp = new byte[81920];
        long sent = 0;
        while (sent < count)
        {
            int toRead = (int)Math.Min(tmp.Length, count - sent);
            int n = src.Read(tmp, 0, toRead);
            if (n <= 0) break;
            _s.Write(tmp, 0, n);
            sent += n;
            onProgress(sent);
        }
        _s.Flush();
    }
}
