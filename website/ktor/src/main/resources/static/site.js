(() => {
  const root = document.documentElement;
  const bands = document.querySelectorAll(".band");
  if (!("IntersectionObserver" in window) || bands.length === 0) {
    root.classList.remove("js");
    return;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    },
    { rootMargin: "0px 0px -8% 0px", threshold: 0.15 },
  );

  bands.forEach((band) => observer.observe(band));

  window.setTimeout(() => {
    observer.disconnect();
    bands.forEach((band) => band.classList.add("is-visible"));
  }, 2500);
})();
