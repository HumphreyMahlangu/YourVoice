import { useEffect, useState } from 'react'
import { getVotingDistricts } from '../api/districts'
import { getMunicipalities, getProvinces } from '../api/profiles'
import type { VotingDistrict } from '../types/district'
import type { LocationSelection, Municipality, Province } from '../types/profile'

/** Optional attributes are independent: an absent ward never blocks a national or provincial record. */
export default function LocationFields({ id, value, onChange, disabled = false }: { id: string; value: LocationSelection; onChange: (value: LocationSelection) => void; disabled?: boolean }) {
  const [data, setData] = useState<{ provinces: Province[]; municipalities: Municipality[]; districts: VotingDistrict[] } | null>(null)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setError(false); setData(null)
    Promise.all([getProvinces(controller.signal), getMunicipalities(controller.signal), getVotingDistricts(controller.signal)])
      .then(([provinces, municipalities, districts]) => { if (!controller.signal.aborted) setData({ provinces, municipalities, districts }) })
      .catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => controller.abort()
  }, [attempt])
  const province = data?.provinces.find((p) => p.id === value.provinceId)
  const municipality = data?.municipalities.find((m) => m.id === value.municipalityId)
  return <fieldset disabled={disabled} className="location-fields">
    <legend>Registered area attributes</legend>
    <p>Leave local attributes blank for national identity only. A request does not verify an area or change eligibility.</p>
    {!data && !error && <p role="status">Loading areas...</p>}
    {error && <div><p role="alert">We couldn't load the areas. Try again.</p><button type="button" onClick={() => setAttempt((n) => n + 1)}>Retry loading areas</button></div>}
    {data && data.provinces.length === 0 && <p role="status">Registration areas haven't been set up yet. National identity verification is still available.</p>}
    <label htmlFor={`${id}-province`}>Province (optional)</label>
    <select id={`${id}-province`} disabled={!data} value={value.provinceId ?? ''} onChange={(e) => onChange(e.target.value ? { provinceId: e.target.value } : {})}>
      <option value="">National identity only</option>{data?.provinces.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
    </select>
    <label htmlFor={`${id}-municipality`}>Municipality (optional)</label>
    <select id={`${id}-municipality`} disabled={!province} value={value.municipalityId ?? ''} onChange={(e) => onChange({ provinceId: value.provinceId, municipalityId: e.target.value || undefined })}>
      <option value="">No verified municipality</option>{data?.municipalities.filter((m) => m.provinceId === value.provinceId).map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
    </select>
    <label htmlFor={`${id}-district`}>Ward area (optional)</label>
    <select id={`${id}-district`} disabled={!municipality} value={value.votingDistrictId ?? ''} onChange={(e) => onChange({ ...value, votingDistrictId: e.target.value || undefined })}>
      <option value="">No verified ward</option>{data?.districts.filter((d) => d.province.trim().toLowerCase() === province?.name.trim().toLowerCase() && d.municipality.trim().toLowerCase() === municipality?.name.trim().toLowerCase()).map((d) => <option key={d.id} value={d.id}>Ward {d.wardNumber} — {d.name} ({d.code})</option>)}
    </select>
  </fieldset>
}
