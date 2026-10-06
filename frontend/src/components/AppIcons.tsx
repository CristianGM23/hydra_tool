import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

function BaseIcon({ children, ...props }: IconProps) {
    return <svg aria-hidden="true" fill="none" height="20" viewBox="0 0 24 24" width="20" {...props}>{children}</svg>;
}

export function FileIcon(props: IconProps) {
    return <BaseIcon {...props}><path d="M6 2.75h7l5 5V21.25H6z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7"/><path d="M13 2.75v5h5" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7"/><path d="M9 13h6M9 16.5h6" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7"/></BaseIcon>;
}

export function FolderTreeIcon(props: IconProps) {
    return <BaseIcon {...props}><path d="M3.5 6.5h6l1.5 2h9.5v10.75h-17z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7"/><path d="M7 11.5v4M7 13.5h4M11 13.5v3M11 16.5h3" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5"/></BaseIcon>;
}

export function FolderIcon(props: IconProps) {
    return <BaseIcon {...props}><path d="M3 6.5h6l2 2h10v10.75H3z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7"/><path d="M3 8.5h18" stroke="currentColor" strokeWidth="1.7"/></BaseIcon>;
}

export function SettingsIcon(props: IconProps) {
    return <BaseIcon {...props}><path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z" stroke="currentColor" strokeWidth="1.7"/><path d="m19 13.2 1.6 1.2-1.8 3.1-2-.8a7.7 7.7 0 0 1-2.1 1.2l-.3 2.1h-3.6l-.3-2.1a7.7 7.7 0 0 1-2.1-1.2l-2 .8-1.8-3.1 1.6-1.2a7.7 7.7 0 0 1 0-2.4L4.6 9.6l1.8-3.1 2 .8a7.7 7.7 0 0 1 2.1-1.2l.3-2.1h3.6l.3 2.1a7.7 7.7 0 0 1 2.1 1.2l2-.8 1.8 3.1-1.6 1.2a7.7 7.7 0 0 1 0 2.4Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.45"/></BaseIcon>;
}

export function ResetIcon(props: IconProps) {
    return <BaseIcon {...props}><path d="M5.5 8.5A7.5 7.5 0 1 1 5 14" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7"/><path d="M4 4.5v5h5" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.7"/></BaseIcon>;
}

export function ProjectIcon(props: IconProps) {
    return <BaseIcon {...props}><rect height="16" rx="2" stroke="currentColor" strokeWidth="1.7" width="17" x="3.5" y="4"/><path d="M7 4V2.75h6V4M7.5 9h9M7.5 13h6" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7"/></BaseIcon>;
}
