interface BrandMarkProps {
  size?: 'small' | 'large'
}

function BrandMark({ size = 'small' }: BrandMarkProps) {
  return (
    <span className={`brand-mark brand-mark--${size}`} aria-hidden="true">
      <span>V</span>
    </span>
  )
}

export default BrandMark
