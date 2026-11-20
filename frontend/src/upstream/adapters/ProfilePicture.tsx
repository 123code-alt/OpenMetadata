/* Licensed under the Apache License, Version 2.0. See LICENSE. */
export default function ProfilePicture({ name }: { name: string }) {
  return (
    <span
      className="tw:flex tw:size-8 tw:items-center tw:justify-center tw:rounded-full tw:bg-brand-primary tw:text-brand-secondary tw:font-medium"
      aria-label={name}>
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}
