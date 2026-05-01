/**
 * Pagination — reusable pagination controls.
 * Requirements: 1.4
 */

const PAGE_SIZE_OPTIONS = [10, 20, 50]

interface PaginationProps {
  /** Current page (0-indexed) */
  page: number
  /** Total number of pages */
  totalPages: number
  /** Total number of elements */
  totalElements: number
  /** Current page size */
  pageSize: number
  /** Called when the user navigates to a different page */
  onPageChange: (page: number) => void
  /** Called when the user changes the page size */
  onPageSizeChange: (size: number) => void
  /** Optional custom page size options */
  pageSizeOptions?: number[]
}

/**
 * Reusable pagination bar with first/prev/page-numbers/next/last controls
 * and a page-size selector.
 */
export default function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = PAGE_SIZE_OPTIONS,
}: PaginationProps) {
  if (totalPages <= 1) return null

  // Show at most 5 page buttons centred around the current page
  const windowSize = Math.min(5, totalPages)
  const startPage = Math.max(0, Math.min(page - 2, totalPages - windowSize))

  return (
    <div
      className="flex items-center justify-between flex-wrap gap-3"
      data-testid="pagination"
      aria-label="Phân trang"
    >
      {/* Page size selector */}
      <div className="flex items-center gap-2 text-body-sm text-on-surface-variant">
        <span>Hiển thị</span>
        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          data-testid="pagination-page-size"
          aria-label="Số mục mỗi trang"
          className="border border-outline-variant rounded-lg px-2 py-1 text-body-sm
                     text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
        >
          {pageSizeOptions.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <span>/ {totalElements} mục</span>
      </div>

      {/* Page buttons */}
      <div className="flex items-center gap-1">
        <button
          onClick={() => onPageChange(0)}
          disabled={page === 0}
          data-testid="pagination-first"
          aria-label="Trang đầu"
          className="p-2 rounded-lg hover:bg-surface-container
                     disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">first_page</span>
        </button>
        <button
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page === 0}
          data-testid="pagination-prev"
          aria-label="Trang trước"
          className="p-2 rounded-lg hover:bg-surface-container
                     disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_left</span>
        </button>

        {Array.from({ length: windowSize }, (_, i) => {
          const pageNum = startPage + i
          return (
            <button
              key={pageNum}
              onClick={() => onPageChange(pageNum)}
              data-testid={`pagination-page-${pageNum}`}
              aria-label={`Trang ${pageNum + 1}`}
              aria-current={pageNum === page ? 'page' : undefined}
              className={`w-9 h-9 rounded-lg text-body-sm font-medium transition-colors
                ${pageNum === page
                  ? 'bg-primary text-white'
                  : 'hover:bg-surface-container text-on-surface'
                }`}
            >
              {pageNum + 1}
            </button>
          )
        })}

        <button
          onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
          disabled={page >= totalPages - 1}
          data-testid="pagination-next"
          aria-label="Trang sau"
          className="p-2 rounded-lg hover:bg-surface-container
                     disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_right</span>
        </button>
        <button
          onClick={() => onPageChange(totalPages - 1)}
          disabled={page >= totalPages - 1}
          data-testid="pagination-last"
          aria-label="Trang cuối"
          className="p-2 rounded-lg hover:bg-surface-container
                     disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">last_page</span>
        </button>
      </div>

      {/* Page info */}
      <div className="text-body-sm text-on-surface-variant" data-testid="pagination-info">
        Trang {page + 1} / {totalPages}
      </div>
    </div>
  )
}
