"use client";

import { FormEvent, useState } from "react";

type JobResponse = {
  jobId: string;
  status: string;
  targetFormat: string;
  originalFileName: string;
  resultFileName?: string | null;
  errorMessage?: string | null;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:13800";

export default function Home() {
  const [file, setFile] = useState<File | null>(null);
  const [job, setJob] = useState<JobResponse | null>(null);
  const [message, setMessage] = useState<string>("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function submitJob(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!file) {
      setMessage("PDF 파일을 선택하세요.");
      return;
    }

    setIsSubmitting(true);
    setMessage("변환 작업을 생성하는 중입니다.");

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("targetFormat", "DOCX");

      const response = await fetch(`${apiBaseUrl}/api/conversion-jobs`, {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error("변환 작업 생성에 실패했습니다.");
      }

      const createdJob = (await response.json()) as JobResponse;
      setJob(createdJob);
      setMessage("변환 작업이 생성되었습니다.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function refreshJob() {
    if (!job) {
      return;
    }

    const response = await fetch(`${apiBaseUrl}/api/conversion-jobs/${job.jobId}`);
    if (response.ok) {
      setJob((await response.json()) as JobResponse);
      setMessage("작업 상태를 갱신했습니다.");
    } else {
      setMessage("작업 상태 조회에 실패했습니다.");
    }
  }

  const canDownload = job?.status === "COMPLETED";

  return (
    <main className="min-h-screen bg-[#f7f8fa] text-[#20242a]">
      <section className="mx-auto flex min-h-screen w-full max-w-5xl flex-col px-6 py-10">
        <header className="mb-8 border-b border-[#d9dee7] pb-5">
          <p className="text-sm font-semibold text-[#315f72]">PDF to editable document</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal">PDF 문서 변환</h1>
          <p className="mt-3 max-w-2xl text-base leading-7 text-[#52606d]">
            PDF를 업로드하면 Spring Boot 백엔드에서 변환 작업을 생성하고, 작업 상태를 확인한 뒤 결과 파일을 다운로드합니다.
          </p>
        </header>

        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <form className="rounded-md border border-[#d9dee7] bg-white p-6" onSubmit={submitJob}>
            <label className="block text-sm font-medium text-[#39424e]" htmlFor="pdf-file">
              PDF 파일
            </label>
            <input
              id="pdf-file"
              className="mt-3 block w-full rounded-md border border-[#c8d0da] px-3 py-2 text-sm"
              type="file"
              accept="application/pdf,.pdf"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />

            <div className="mt-5 rounded-md bg-[#eef5f3] p-4 text-sm text-[#315f72]">
              현재 MVP 대상 형식은 DOCX입니다. OCR과 HWP는 로드맵에 따라 이후 단계에서 확장합니다.
            </div>

            <button
              className="mt-5 rounded-md bg-[#234c5f] px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-[#9aa8b3]"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? "생성 중" : "변환 시작"}
            </button>
          </form>

          <aside className="rounded-md border border-[#d9dee7] bg-white p-6">
            <h2 className="text-lg font-semibold">작업 상태</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div>
                <dt className="text-[#6a7581]">상태</dt>
                <dd className="font-medium">{job?.status ?? "작업 없음"}</dd>
              </div>
              <div>
                <dt className="text-[#6a7581]">파일명</dt>
                <dd className="break-all font-medium">{job?.originalFileName ?? "-"}</dd>
              </div>
              <div>
                <dt className="text-[#6a7581]">대상 형식</dt>
                <dd className="font-medium">{job?.targetFormat ?? "-"}</dd>
              </div>
            </dl>

            <div className="mt-5 flex flex-wrap gap-2">
              <button
                className="rounded-md border border-[#aeb8c3] px-3 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:text-[#9aa8b3]"
                type="button"
                onClick={refreshJob}
                disabled={!job}
              >
                상태 갱신
              </button>
              <a
                className={`rounded-md px-3 py-2 text-sm font-medium ${
                  canDownload ? "bg-[#3d5f42] text-white" : "pointer-events-none bg-[#d9dee7] text-[#7b8794]"
                }`}
                href={job ? `${apiBaseUrl}/api/conversion-jobs/${job.jobId}/download` : "#"}
              >
                다운로드
              </a>
            </div>

            {message ? <p className="mt-4 text-sm text-[#52606d]">{message}</p> : null}
            {job?.errorMessage ? <p className="mt-3 text-sm text-[#b42318]">{job.errorMessage}</p> : null}
          </aside>
        </div>
      </section>
    </main>
  );
}
