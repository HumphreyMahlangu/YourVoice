function areaLabel(province?: string | null, municipality?: string | null, ward?: number | null) {
  return [province, municipality, ward != null ? `Ward ${ward}` : null].filter(Boolean).join(' · ') || 'National identity only — no verified local area'
}
export default function RegisteredArea({ province, municipality, wardNumber }: { province?: string | null; municipality?: string | null; wardNumber?: number | null }) {
  return <span>{areaLabel(province, municipality, wardNumber)}</span>
}
