package com.benchmark;

import org.jocl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.jocl.CL.*;

/**
 * Três implementações de contagem de palavra em texto:
 *  1. serialCPU– loop simples, thread única
 *  2. parallelCPU– ForkJoinPool / ExecutorService, multi-thread
 *  3. parallelGPU– OpenCL via JOCL, execução na GPU
 */
public class WordSearcher {

    // ---------------------------------------------------------------
    // 1. SERIAL CPU
    // ---------------------------------------------------------------

    public long serialCPU(String text, String word) {
        String lowerText = text.toLowerCase();
        String lowerWord = word.toLowerCase();
        String[] tokens  = lowerText.split("\\W+");
        long count = 0;
        for (String token : tokens) {
            if (token.equals(lowerWord)) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------
    // 2. PARALLEL CPU
    // ---------------------------------------------------------------
    public long parallelCPU(String text, String word) throws InterruptedException, ExecutionException {
        int cores      = Runtime.getRuntime().availableProcessors();
        String lower   = text.toLowerCase();
        String lWord   = word.toLowerCase();
        String[] tokens = lower.split("\\W+");

        ExecutorService pool = Executors.newFixedThreadPool(cores);
        List<Future<Long>> futures = new ArrayList<>();

        int chunkSize = Math.max(1, tokens.length / cores);

        for (int i = 0; i < cores; i++) {
            final int start = i * chunkSize;
            final int end   = (i == cores - 1) ? tokens.length : start + chunkSize;
            final String[] chunk = tokens;

            futures.add(pool.submit(() -> {
                long c = 0;
                for (int j = start; j < end; j++) {
                    if (chunk[j].equals(lWord)) c++;
                }
                return c;
            }));
        }

        long total = 0;
        for (Future<Long> f : futures) {
            total += f.get();
        }
        pool.shutdown();
        return total;
    }

    // ---------------------------------------------------------------
    // 3. PARALLEL GPU (OpenCL via JOCL)
    // ---------------------------------------------------------------
    public long parallelGPU(String text, String word) {
        try {
            return gpuCount(text, word);
        } catch (Exception e) {
            System.err.println("  [GPU] OpenCL não disponível (" + e.getMessage() + "). Usando fallback serial.");
            return serialCPU(text, word);
        }
    }

    private long gpuCount(String text, String word) {
        String lText = text.toLowerCase();
        String lWord = word.toLowerCase();
        int textLen  = lText.length();
        int wordLen  = lWord.length();

        byte[] textBytes = lText.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] wordBytes = lWord.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        // ---- Kernel OpenCL ----
        String kernelSource =
            "__kernel void countWord(\n" +
            "    __global const char* text,\n" +
            "    __global const char* word,\n" +
            "    const int textLen,\n" +
            "    const int wordLen,\n" +
            "    __global volatile int* result)\n" +
            "{\n" +
            "    int i = get_global_id(0);\n" +
            "    if (i + wordLen > textLen) return;\n" +
            "\n" +
            "    // Verifica se os chars batem\n" +
            "    for (int k = 0; k < wordLen; k++) {\n" +
            "        if (text[i + k] != word[k]) return;\n" +
            "    }\n" +
            "\n" +
            "    // Verifica fronteira esquerda\n" +
            "    if (i > 0) {\n" +
            "        char prev = text[i - 1];\n" +
            "        if ((prev >= 'a' && prev <= 'z') || (prev >= '0' && prev <= '9')) return;\n" +
            "    }\n" +
            "\n" +
            "    // Verifica fronteira direita\n" +
            "    if (i + wordLen < textLen) {\n" +
            "        char next = text[i + wordLen];\n" +
            "        if ((next >= 'a' && next <= 'z') || (next >= '0' && next <= '9')) return;\n" +
            "    }\n" +
            "\n" +
            "    atomic_add(result, 1);\n" +
            "}\n";

        CL.setExceptionsEnabled(true);

        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        if (numPlatforms[0] == 0) throw new RuntimeException("Nenhuma plataforma OpenCL");

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[0];

        cl_device_id device = getDevice(platform, CL_DEVICE_TYPE_GPU);
        if (device == null) device = getDevice(platform, CL_DEVICE_TYPE_CPU);
        if (device == null) throw new RuntimeException("Nenhum dispositivo OpenCL disponível");

        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, platform);

        cl_context       context  = clCreateContext(props, 1, new cl_device_id[]{device}, null, null, null);
        cl_command_queue queue    = clCreateCommandQueueWithProperties(context, device, null, null);
        cl_program       program  = clCreateProgramWithSource(context, 1, new String[]{kernelSource}, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        cl_kernel        kernel   = clCreateKernel(program, "countWord", null);

        cl_mem textMem  = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                                         Sizeof.cl_char * textBytes.length,
                                         Pointer.to(textBytes), null);
        cl_mem wordMem  = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                                         Sizeof.cl_char * wordBytes.length,
                                         Pointer.to(wordBytes), null);
        int[] resultArr = new int[]{0};
        cl_mem resultMem = clCreateBuffer(context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                                          Sizeof.cl_int,
                                          Pointer.to(resultArr), null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textMem));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(wordMem));
        clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{textLen}));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{wordLen}));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(resultMem));

        long[] globalSize = new long[]{textLen};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalSize, null, 0, null, null);
        clFinish(queue);

        clEnqueueReadBuffer(queue, resultMem, CL_TRUE, 0,
                            Sizeof.cl_int, Pointer.to(resultArr), 0, null, null);

        clReleaseMemObject(textMem);
        clReleaseMemObject(wordMem);
        clReleaseMemObject(resultMem);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(context);

        return resultArr[0];
    }

    private cl_device_id getDevice(cl_platform_id platform, long deviceType) {
        try {
            int[] count = new int[1];
            clGetDeviceIDs(platform, deviceType, 0, null, count);
            if (count[0] == 0) return null;
            cl_device_id[] devices = new cl_device_id[count[0]];
            clGetDeviceIDs(platform, deviceType, count[0], devices, null);
            return devices[0];
        } catch (CLException e) {
            return null;
        }
    }
}
