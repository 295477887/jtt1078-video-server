package cn.org.hentai.jtt1078.media.publisher;

import cn.org.hentai.jtt1078.audio.ADPCMCodec;
import cn.org.hentai.jtt1078.audio.AudioCodec;
import cn.org.hentai.jtt1078.audio.RawDataCopyCodec;
import cn.org.hentai.jtt1078.media.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedList;

/**
 * Created by matrixy on 2019/12/14.
 */
public class AudioPublisher extends MediaStreamPublisher
{
    static Logger logger = LoggerFactory.getLogger(AudioPublisher.class);

    // 静音PCM数据包
    static final byte[] SILENCE_PCM_DATA = new byte[320];

    // 已经接收到的音频消息包个数
    int packetCount = 0;

    // 是否已经进入静音模式？
    boolean silentMode = false;

    public AudioPublisher(long channel, String tag, Process process)
    {
        super(channel, tag, process);
    }

    @Override
    public void transcodeTo(Media.Encoding mediaEncoding, byte[] data, OutputStream output) throws Exception
    {
        AudioCodec codec = AudioCodec.getCodec(mediaEncoding);
        output.write(codec.toPCM(data));
        output.flush();
    }

    @Override
    public void run()
    {
        long startTime = System.currentTimeMillis();
        while (!Thread.interrupted())
        {
            try
            {
                MediaPacket packet = null;
                synchronized (lock)
                {
                    while (silentMode == false && Thread.interrupted() == false && packets.size() == 0)
                    {
                        lock.wait(100);
                        // 等待5秒还没有收到音频数据包，那就进入静音模式
                        if (System.currentTimeMillis() - startTime > 1000 * 5 && packetCount == 0)
                        {
                            silentMode = true;
                            break;
                        }
                    }
                    if (silentMode == false)
                    {
                        if (packets.size() == 0) break;
                        packet = packets.removeFirst();
                    }
                }

                if (output == null)
                {
                    output = new FileOutputStream(fifoPath);
                    Thread.sleep(100);
                }
                publishing = true;
                try
                {
                    if (silentMode == false) transcodeTo(packet.mediaEncoding, packet.mediaData, output);
                    else
                    {
                        output.write(SILENCE_PCM_DATA);
                        output.flush();
                    }
                }
                catch(Exception e)
                {
                    throw new RuntimeException(e);
                }
                publishing = false;
                this.lastActiveTime = System.currentTimeMillis();
            }
            catch(Exception ex)
            {
                logger.error("data transcode error", ex);
                break;
            }
        }
    }
}
