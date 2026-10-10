import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BlurredImage } from './BlurredImage.tsx';

describe('BlurredImage', () => {
  it('given a photo still loading, when rendered, then the blurred placeholder shows and the photo has alt text', () => {
    render(<BlurredImage src="/uploads/p.webp" alt="Pijama corto, rosa, frente" />);

    expect(screen.getByTestId('blurred-image')).not.toHaveClass('loaded');
    expect(screen.getByRole('img')).toHaveAttribute('alt', 'Pijama corto, rosa, frente');
    expect(screen.getByRole('img')).toHaveAttribute('loading', 'lazy');
  });

  it('given the photo finished loading, when the load event fires, then the placeholder fades out', () => {
    render(<BlurredImage src="/uploads/p.webp" alt="Pijama corto, rosa, frente" />);

    fireEvent.load(screen.getByRole('img'));

    expect(screen.getByTestId('blurred-image')).toHaveClass('loaded');
  });

  it('given a thumbnail of the photo, when rendered, then it is used as the blurred background', () => {
    render(<BlurredImage src="/uploads/p.webp" alt="Pijama" placeholder="data:image/webp;base64,AAAA" />);

    expect(screen.getByTestId('blurred-image').style.getPropertyValue('--ph-placeholder'))
      .toBe('url("data:image/webp;base64,AAAA")');
  });

  it('given a placeholder that tries to break out of the CSS value, when rendered, then it is ignored', () => {
    render(<BlurredImage src="/uploads/p.webp" alt="Pijama" placeholder='data:image/png") ; background: url("https://evil.example/x' />);

    expect(screen.getByTestId('blurred-image').style.getPropertyValue('--ph-placeholder')).toBe('');
  });
});
